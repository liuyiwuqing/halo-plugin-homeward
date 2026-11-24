package site.muyin.homeward.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.MetadataUtil;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import site.muyin.homeward.enums.ClueStatus;
import site.muyin.homeward.model.ClueReportVO;
import site.muyin.homeward.model.ClueStatusUpdateRequest;
import site.muyin.homeward.model.ClueSubmitRequest;
import site.muyin.homeward.model.ClueSubmitResult;
import site.muyin.homeward.notify.ClueSubmittedNotifyService;
import site.muyin.homeward.query.ClueReportQuery;
import site.muyin.homeward.query.MissingPersonCasePublicQuery;
import site.muyin.homeward.scheme.ClueReport;
import site.muyin.homeward.scheme.MissingPersonCase;
import site.muyin.homeward.service.ClueProtectionService;
import site.muyin.homeward.service.ClueReportService;
import site.muyin.homeward.setting.HomewardPublicSettings;
import site.muyin.homeward.support.HomewardNames;
import site.muyin.homeward.support.HomewardRequestSupport;
import site.muyin.homeward.support.ReceiptSupport;

import java.time.Instant;
import java.util.ArrayList;

import static run.halo.app.extension.index.query.Queries.equal;

@Service
@RequiredArgsConstructor
public class ClueReportServiceImpl implements ClueReportService {

    private final ReactiveExtensionClient client;

    private final HomewardPublicSettings publicSettings;

    private final ClueProtectionService protectionService;

    private final ClueSubmittedNotifyService notifyService;

    @Override
    public Mono<ListResult<ClueReportVO>> listForConsole(ClueReportQuery query) {
        return client.listBy(ClueReport.class, query.toListOptions(), query.toPageRequest())
            .map(result -> new ListResult<>(result.getPage(), result.getSize(), result.getTotal(),
                result.getItems().stream().map(ClueReportVO::from).toList()));
    }

    @Override
    public Mono<ClueReportVO> getForConsole(String name) {
        return client.fetch(ClueReport.class, name)
            .switchIfEmpty(Mono.error(notFound("线索不存在")))
            .map(ClueReportVO::from);
    }

    @Override
    public Mono<ClueReportVO> updateStatus(String name, ClueStatusUpdateRequest request,
                                          String authenticatedHandler) {
        if (request == null || request.getStatus() == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "线索状态不能为空"));
        }
        if (authenticatedHandler == null || authenticatedHandler.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录"));
        }
        if (request.getProcessNote() != null && request.getProcessNote().length() > 2000) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "处理备注不能超过 2000 个字符"));
        }
        return client.fetch(ClueReport.class, name)
            .switchIfEmpty(Mono.error(notFound("线索不存在")))
            .map(item -> {
                var spec = item.getSpec();
                Instant now = Instant.now();
                var history = new ArrayList<>(spec.getProcessingHistory() == null
                    ? java.util.List.<ClueReport.ProcessingRecord>of() : spec.getProcessingHistory());
                // 旧版本只保留最近一次处理，不捏造更早历史；首次追加时保留这份旧记录。
                if (history.isEmpty() && (trimToNull(spec.getProcessNote()) != null
                    || trimToNull(spec.getHandler()) != null
                    || (spec.getStatus() != null && spec.getStatus() != ClueStatus.PENDING))) {
                    var legacy = new ClueReport.ProcessingRecord();
                    legacy.setStatus(spec.getStatus());
                    legacy.setHandler(spec.getHandler());
                    legacy.setProcessNote(spec.getProcessNote());
                    legacy.setProcessedAt(spec.getUpdatedAt());
                    legacy.setLegacy(true);
                    history.add(legacy);
                }
                var record = new ClueReport.ProcessingRecord();
                record.setPreviousStatus(spec.getStatus());
                record.setStatus(request.getStatus());
                record.setHandler(authenticatedHandler);
                record.setProcessNote(trimToNull(request.getProcessNote()));
                record.setProcessedAt(now);
                history.add(record);
                spec.setProcessingHistory(history);
                spec.setStatus(request.getStatus());
                spec.setHandler(authenticatedHandler);
                spec.setProcessNote(record.getProcessNote());
                spec.setUpdatedAt(now);
                MetadataUtil.nullSafeLabels(item).put("homeward.muyin.site/status", spec.getStatus().name());
                return item;
            })
            .flatMap(client::update)
            .map(ClueReportVO::from);
    }

    @Override
    public Mono<ClueReportVO> delete(String name) {
        return client.fetch(ClueReport.class, name)
            .switchIfEmpty(Mono.error(notFound("线索不存在")))
            .flatMap(client::delete)
            .map(ClueReportVO::from);
    }

    @Override
    public Mono<ClueSubmitResult> submit(String caseSlug, ClueSubmitRequest request,
                                         ServerRequest serverRequest) {
        return publicSettings.requireEnabled()
            .then(Mono.defer(() -> findPublishedCase(caseSlug)))
            .zipWith(publicSettings.protection())
            .flatMap(tuple -> {
                var caseItem = tuple.getT1();
                var protection = tuple.getT2();
                String ipHash = HomewardRequestSupport.sha256(
                    HomewardRequestSupport.clientIp(serverRequest),
                    protection.ipHashSaltOrDefault());
                var decision = protectionService.tryAccept(caseSlug, ipHash, request, protection);
                if (!decision.isAllowed()) {
                    return Mono.error(new ResponseStatusException(decision.getRejectionStatus(),
                        decision.getReason()));
                }
                String token = ReceiptSupport.generateToken();
                return createClue(caseItem, request, ipHash,
                    HomewardRequestSupport.userAgent(serverRequest), ReceiptSupport.hashToken(token))
                    .flatMap(clue -> notifyService.notifySubmitted(clue)
                        .onErrorResume(error -> Mono.empty())
                        .thenReturn(ClueSubmitResult.builder()
                            .status(clue.getSpec().getStatus().name())
                            .message("线索已提交，请保存回执凭证以查询处理进度")
                            .receiptToken(token)
                            .build()));
            });
    }

    @Override
    public Mono<Long> countByStatus(ClueStatus status) {
        var options = ListOptions.builder()
            .andQuery(equal("spec.status", status.name()))
            .build();
        return client.listAll(ClueReport.class, options, Sort.unsorted()).count();
    }

    private Mono<ClueReport> createClue(MissingPersonCase caseItem, ClueSubmitRequest request,
                                        String ipHash, String userAgent, String receiptTokenHash) {
        var clue = new ClueReport();
        clue.setMetadata(new Metadata());
        clue.getMetadata().setName(ClueReport.EXTENSION_NAME_PREFIX + HomewardNames.randomSuffix());
        clue.getMetadata().setGenerateName(ClueReport.EXTENSION_NAME_PREFIX);
        var spec = clue.getSpec();
        spec.setCaseName(caseItem.getMetadata().getName());
        spec.setCaseSlug(caseItem.getSpec().getSlug());
        spec.setCaseTitle(caseItem.getSpec().getTitle());
        spec.setSubmitterName(trimToNull(request.getSubmitterName()));
        spec.setContact(trimToNull(request.getContact()));
        spec.setContent(trimToNull(request.getContent()));
        spec.setLocation(trimToNull(request.getLocation()));
        spec.setIpHash(ipHash);
        spec.setUserAgent(userAgent);
        spec.setReceiptTokenHash(receiptTokenHash);
        spec.setStatus(ClueStatus.PENDING);
        spec.setSubmittedAt(Instant.now());
        spec.setUpdatedAt(spec.getSubmittedAt());
        MetadataUtil.nullSafeLabels(clue).put("homeward.muyin.site/status", spec.getStatus().name());
        MetadataUtil.nullSafeAnnotations(clue);
        return client.create(clue);
    }

    private Mono<MissingPersonCase> findPublishedCase(String slug) {
        var options = ListOptions.builder(MissingPersonCasePublicQuery.publishedOptions())
            .andQuery(equal("spec.slug", slug))
            .build();
        return client.listBy(MissingPersonCase.class, options, PageRequestImpl.of(1, 1))
            .flatMap(result -> result.getItems().stream().findFirst()
                .map(Mono::just)
                .orElseGet(() -> Mono.error(notFound("寻亲案例不存在或未发布"))));
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
