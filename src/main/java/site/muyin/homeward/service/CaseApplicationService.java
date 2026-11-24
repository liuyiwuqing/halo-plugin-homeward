package site.muyin.homeward.service;

import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
import site.muyin.homeward.enums.CaseStatus;
import site.muyin.homeward.model.CaseApplicationRequest;
import site.muyin.homeward.scheme.CaseApplication;
import site.muyin.homeward.scheme.MissingPersonCase;
import site.muyin.homeward.setting.HomewardPublicSettings;
import site.muyin.homeward.support.HomewardRequestSupport;

@Service
@RequiredArgsConstructor
public class CaseApplicationService {
    private static final String ORIGIN = "homeward.muyin.site/application";
    private final ReactiveExtensionClient client;
    private final HomewardPublicSettings settings;
    // Bounded, expiring buckets. A restart resets this in-process abuse guard.
    private final Map<String, Bucket> buckets = new HashMap<>();
    private record Bucket(long hour, int count) { }

    public Mono<Boolean> enabled() {
        return settings.general().map(value -> value.enabledOrDefault()
            && Boolean.TRUE.equals(value.getApplicationSubmissionEnabled()));
    }

    public Mono<Map<String, String>> submit(CaseApplicationRequest body, ServerRequest request) {
        return enabled().flatMap(enabled -> {
            if (!enabled) return Mono.error(error(HttpStatus.SERVICE_UNAVAILABLE, "寻亲申请暂未开放"));
            validate(body);
            // Halo/proxy resolves the remote address. Never trust arbitrary forwarded headers here.
            String address = request.remoteAddress().map(value -> value.getHostString()).orElse("unknown");
            accept(HomewardRequestSupport.sha256(address, "homeward-applications"));
            var item = new CaseApplication();
            item.setMetadata(new Metadata());
            item.getMetadata().setName("homeward-application-" + UUID.randomUUID());
            var spec = item.getSpec();
            spec.setPersonName(body.getPersonName().trim());
            spec.setMissingDate(trim(body.getMissingDate()));
            spec.setMissingLocation(body.getMissingLocation().trim());
            spec.setDescription(body.getDescription().trim());
            spec.setApplicantName(body.getApplicantName().trim());
            spec.setRelationship(body.getRelationship().trim());
            spec.setContact(body.getContact().trim());
            spec.setSourceUrl(trim(body.getSourceUrl()));
            spec.setSubmittedAt(Instant.now());
            spec.setUpdatedAt(spec.getSubmittedAt());
            spec.setConsentAt(spec.getSubmittedAt());
            return client.create(item).map(saved -> Map.of("status", "PENDING",
                "message", "申请已提交，请等待管理员通过您留下的联系方式核实。资料审核前不会公开。"));
        });
    }

    public Mono<ListResult<CaseApplication>> list(int page, int size, String status) {
        var options = ListOptions.builder().andQuery(isNull("metadata.deletionTimestamp"));
        if (status != null && !status.isBlank()) {
            try { CaseApplication.Status.valueOf(status); }
            catch (IllegalArgumentException e) { return Mono.error(error(HttpStatus.BAD_REQUEST, "申请状态无效")); }
            options.andQuery(equal("spec.status", status));
        }
        if (page < 1 || size < 1 || size > 50) return Mono.error(error(HttpStatus.BAD_REQUEST, "分页参数无效"));
        return client.listBy(CaseApplication.class, options.build(), PageRequestImpl.of(page, size,
            Sort.by("spec.submittedAt").descending().and(Sort.by("metadata.name"))));
    }

    public Mono<CaseApplication> review(String name, boolean accept, String note, String handler) {
        if (handler == null || handler.isBlank()) return Mono.error(error(HttpStatus.UNAUTHORIZED, "请先登录"));
        if (note != null && note.length() > 2000) return Mono.error(error(HttpStatus.BAD_REQUEST, "审核备注不能超过 2000 字"));
        return client.fetch(CaseApplication.class, name)
            .filter(item -> item.getMetadata().getDeletionTimestamp() == null)
            .switchIfEmpty(Mono.error(error(HttpStatus.NOT_FOUND, "申请不存在")))
            .flatMap(item -> {
                var spec = item.getSpec();
                if (accept && spec.getStatus() == CaseApplication.Status.ACCEPTED) return Mono.just(item);
                if (spec.getStatus() == CaseApplication.Status.REJECTED
                    || (!accept && spec.getStatus() != CaseApplication.Status.PENDING)) {
                    return Mono.error(error(HttpStatus.CONFLICT, "申请已处理或正在转为草稿，请刷新后查看"));
                }
                if (accept && spec.getStatus() == CaseApplication.Status.CONVERTING) return convert(item);
                spec.setHandler(handler);
                spec.setReviewNote(trim(note));
                spec.setUpdatedAt(Instant.now());
                spec.setStatus(accept ? CaseApplication.Status.CONVERTING : CaseApplication.Status.REJECTED);
                // Reserve the review via extension optimistic locking before creating the draft.
                return client.update(item).flatMap(saved -> accept ? convert(saved) : Mono.just(saved));
            });
    }

    private Mono<CaseApplication> convert(CaseApplication application) {
        String slug = "application-" + application.getMetadata().getName().substring("homeward-application-".length());
        String draftName = MissingPersonCase.EXTENSION_NAME_PREFIX + slug;
        return client.fetch(MissingPersonCase.class, draftName)
            .switchIfEmpty(Mono.defer(() -> {
                var draft = new MissingPersonCase();
                draft.setMetadata(new Metadata());
                draft.getMetadata().setName(draftName);
                MetadataUtil.nullSafeAnnotations(draft).put(ORIGIN, application.getMetadata().getName());
                var source = application.getSpec();
                var target = draft.getSpec();
                target.setSlug(slug);
                target.setPersonName(source.getPersonName());
                target.setTitle(source.getPersonName() + "的寻亲信息");
                target.setMissingDate(source.getMissingDate());
                target.setMissingLocation(source.getMissingLocation());
                target.setDescription(source.getDescription());
                target.setSourceUrl(source.getSourceUrl());
                target.setStatus(CaseStatus.DRAFT);
                target.setCreatedAt(Instant.now());
                target.setUpdatedAt(target.getCreatedAt());
                // Applicant identity/contact stay in the private application, not public case fields.
                MetadataUtil.nullSafeLabels(draft).put("homeward.muyin.site/status", "DRAFT");
                return client.create(draft);
            }))
            .flatMap(draft -> {
                if (draft.getMetadata().getDeletionTimestamp() != null
                    || !application.getMetadata().getName().equals(
                        MetadataUtil.nullSafeAnnotations(draft).get(ORIGIN))) {
                    return Mono.error(error(HttpStatus.CONFLICT, "关联草稿不可用，请管理员核查后再处理"));
                }
                application.getSpec().setDraftName(draftName);
                application.getSpec().setStatus(CaseApplication.Status.ACCEPTED);
                application.getSpec().setUpdatedAt(Instant.now());
                return client.update(application);
            });
    }

    private synchronized void accept(String key) {
        long hour = Instant.now().getEpochSecond() / 3600;
        buckets.entrySet().removeIf(entry -> entry.getValue().hour() != hour);
        var previous = buckets.getOrDefault(key, new Bucket(hour, 0));
        if (previous.count() >= 3 || (!buckets.containsKey(key) && buckets.size() >= 10000)) {
            throw error(HttpStatus.TOO_MANY_REQUESTS, "申请提交太频繁，请稍后再试");
        }
        buckets.put(key, new Bucket(hour, previous.count() + 1));
    }

    private static void validate(CaseApplicationRequest body) {
        if (body == null || !Boolean.TRUE.equals(body.getConsent())) throw error(HttpStatus.BAD_REQUEST, "请确认资料授权和联系核实说明");
        field(body.getPersonName(), 80, true, "寻亲人员姓名");
        field(body.getMissingLocation(), 200, true, "失踪地点");
        field(body.getMissingDate(), 40, false, "失踪时间");
        field(body.getDescription(), 4000, true, "详细情况");
        if (body.getDescription().trim().length() < 20) throw error(HttpStatus.BAD_REQUEST, "详细情况至少填写 20 字");
        field(body.getApplicantName(), 80, true, "申请人姓名");
        field(body.getRelationship(), 80, true, "与寻亲人员的关系");
        field(body.getContact(), 120, true, "联系方式");
        field(body.getSourceUrl(), 1000, false, "来源链接");
        if (trim(body.getWebsite()) != null) throw error(HttpStatus.BAD_REQUEST, "提交异常，请刷新页面后重试");
        if (trim(body.getSourceUrl()) != null) {
            try {
                URI uri = URI.create(body.getSourceUrl().trim());
                if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || body.getSourceUrl().contains("\\")) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) { throw error(HttpStatus.BAD_REQUEST, "来源链接须为有效的 http 或 https 地址"); }
        }
    }

    private static void field(String value, int max, boolean required, String label) {
        if (required && trim(value) == null) throw error(HttpStatus.BAD_REQUEST, "请填写" + label);
        if (value != null && value.length() > max) throw error(HttpStatus.BAD_REQUEST, label + "超出长度限制");
    }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
