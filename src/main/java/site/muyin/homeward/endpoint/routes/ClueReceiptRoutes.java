package site.muyin.homeward.endpoint.routes;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import site.muyin.homeward.service.ClueReceiptService;
import site.muyin.homeward.support.ReceiptSupport;

@Component
@RequiredArgsConstructor
public class ClueReceiptRoutes {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ClueReceiptService service;
    private final ReceiptSupport receiptSupport;

    public Mono<ServerResponse> lookupForPublic(ServerRequest request) {
        return Mono.defer(() -> {
            receiptSupport.checkLookupRate(request);
            // 同时约束 Content-Length 和分块请求，凭证不进入 URL 或访问日志。
            return DataBufferUtils.join(request.body(BodyExtractors.toDataBuffers()), 1024)
                .map(buffer -> {
                    try {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        var body = JSON.readTree(bytes);
                        var token = body == null ? null : body.get("receiptToken");
                        if (token == null || !token.isTextual()
                            || !ReceiptSupport.validToken(token.textValue())) throw notFound();
                        return token.textValue();
                    } catch (Exception error) {
                        throw notFound();
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .onErrorMap(org.springframework.core.io.buffer.DataBufferLimitException.class,
                    ignored -> notFound())
                .switchIfEmpty(Mono.error(notFound()))
                .flatMap(service::lookup);
        }).flatMap(result -> ServerResponse.ok().header("Cache-Control", "no-store")
            .bodyValue(result))
            .onErrorResume(ResponseStatusException.class, error -> ServerResponse.status(error.getStatusCode())
                .header("Cache-Control", "no-store")
                .bodyValue(Map.of("detail", error.getReason() == null ? "查询暂不可用" : error.getReason())))
            .onErrorResume(error -> ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Cache-Control", "no-store").bodyValue(Map.of("detail", "查询暂不可用，请稍后再试")));
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "回执不存在，请核对凭证");
    }
}
