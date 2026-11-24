package site.muyin.homeward.service;

import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import site.muyin.homeward.enums.ClueStatus;
import site.muyin.homeward.model.ClueReceiptVO;
import site.muyin.homeward.scheme.ClueReport;
import site.muyin.homeward.setting.HomewardPublicSettings;
import site.muyin.homeward.support.ReceiptSupport;

@Service
@RequiredArgsConstructor
public class ClueReceiptService {
    private final ReactiveExtensionClient client;
    private final HomewardPublicSettings publicSettings;

    public Mono<ClueReceiptVO> lookup(String token) {
        if (!ReceiptSupport.validToken(token)) return Mono.error(notFound());
        var options = ListOptions.builder()
            .andQuery(equal("spec.receiptTokenHash", ReceiptSupport.hashToken(token)))
            .andQuery(isNull("metadata.deletionTimestamp"))
            .build();
        return publicSettings.requireEnabled()
            .then(Mono.defer(() -> client.listBy(ClueReport.class, options, PageRequestImpl.of(1, 1))))
            .flatMap(result -> result.getItems().stream().findFirst().map(Mono::just)
                .orElseGet(() -> Mono.error(notFound())))
            .map(item -> {
                var spec = item.getSpec();
                ClueStatus status = spec.getStatus() == null ? ClueStatus.PENDING : spec.getStatus();
                String message = switch (status) {
                    case PENDING -> "线索已收到，等待工作人员核实。";
                    case CONTACTED -> "工作人员已开始联系核实，请留意您提交的联系方式。";
                    case RESOLVED -> "这条线索的处理已完成，感谢您的帮助。";
                    case INVALID -> "这条线索暂未被采用，感谢您的关注与帮助。";
                };
                return new ClueReceiptVO(status, spec.getSubmittedAt(), spec.getUpdatedAt(), message);
            });
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "回执不存在，请核对凭证");
    }
}
