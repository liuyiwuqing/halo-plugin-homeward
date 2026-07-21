package site.muyin.homeward.router;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.theme.TemplateNameResolver;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class HomewardPageRouter {

    private final TemplateNameResolver templateNameResolver;

    @Bean
    RouterFunction<ServerResponse> homewardPageRouterFunction() {
        return route(GET("/homeward"), this::renderHomewardPage);
    }

    private Mono<ServerResponse> renderHomewardPage(ServerRequest request) {
        return templateNameResolver.resolveTemplateNameOrDefault(request.exchange(), "homeward")
            .flatMap(template -> ServerResponse.ok()
                .contentType(MediaType.TEXT_HTML)
                .render(template, Map.of(
                    "homewardApi", "/apis/public.homeward.muyin.site/v1alpha1"
                )));
    }
}
