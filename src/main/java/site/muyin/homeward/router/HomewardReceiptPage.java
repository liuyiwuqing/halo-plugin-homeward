package site.muyin.homeward.router;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.theme.TemplateNameResolver;
import run.halo.app.theme.router.ModelConst;

@Component
@RequiredArgsConstructor
public class HomewardReceiptPage {
    private final TemplateNameResolver templateNameResolver;

    public Mono<ServerResponse> render(ServerRequest request) {
        return templateNameResolver.resolveTemplateNameOrDefault(request.exchange(), "homeward-receipt")
            .flatMap(template -> ServerResponse.ok().contentType(MediaType.TEXT_HTML)
                .header("Cache-Control", "no-store").header("Referrer-Policy", "no-referrer")
                .render(template, Map.of(ModelConst.TEMPLATE_ID, "plugin:homeward:receipt")));
    }
}
