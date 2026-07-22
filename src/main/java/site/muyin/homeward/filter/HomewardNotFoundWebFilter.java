package site.muyin.homeward.filter;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.app.security.AdditionalWebFilter;
import site.muyin.homeward.setting.HomewardDisplaySetting;
import site.muyin.homeward.setting.HomewardGeneralSetting;
import site.muyin.homeward.support.HomewardPathSupport;

@Component
@RequiredArgsConstructor
public class HomewardNotFoundWebFilter implements AdditionalWebFilter {

    public static final String NOT_FOUND_ATTRIBUTE =
        HomewardNotFoundWebFilter.class.getName() + ".NOT_FOUND";

    private static final String HOMEWARD_PATH = "/homeward";

    private final ReactiveSettingFetcher settingFetcher;

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (exchange.getRequest().getMethod() != HttpMethod.GET
            || HomewardPathSupport.shouldSkip(path)) {
            return chain.filter(exchange);
        }

        return chain.filter(exchange)
            .onErrorResume(error -> handleNotFound(exchange, chain, error));
    }

    private Mono<Void> handleNotFound(ServerWebExchange exchange,
                                      WebFilterChain chain,
                                      Throwable error) {
        if (!isNotFound(error) || exchange.getResponse().isCommitted()) {
            return Mono.error(error);
        }
        return injectionEnabled()
            .flatMap(enabled -> enabled
                ? renderHomewardNotFound(exchange, chain)
                : Mono.error(error))
            .onErrorResume(renderError -> Mono.error(error));
    }

    private Mono<Void> renderHomewardNotFound(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getAttributes().put(NOT_FOUND_ATTRIBUTE, true);
        var request = exchange.getRequest().mutate().path(HOMEWARD_PATH).build();
        var response = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public boolean setStatusCode(HttpStatusCode status) {
                return super.setStatusCode(HttpStatus.NOT_FOUND);
            }
        };
        response.setStatusCode(HttpStatus.NOT_FOUND);
        var rewrittenExchange = exchange.mutate()
            .request(request)
            .response(response)
            .build();
        return chain.filter(rewrittenExchange);
    }

    private boolean isNotFound(Throwable error) {
        return error instanceof ErrorResponse errorResponse
            && errorResponse.getStatusCode().value() == HttpStatus.NOT_FOUND.value();
    }

    private Mono<Boolean> injectionEnabled() {
        return Mono.zip(
                settingFetcher.fetch(HomewardGeneralSetting.GROUP, HomewardGeneralSetting.class)
                    .defaultIfEmpty(HomewardGeneralSetting.defaults()),
                settingFetcher.fetch(HomewardDisplaySetting.GROUP, HomewardDisplaySetting.class)
                    .defaultIfEmpty(HomewardDisplaySetting.defaults())
            )
            .map(tuple -> tuple.getT1().enabledOrDefault() && tuple.getT2().inject404OrDefault())
            .onErrorReturn(false);
    }
}
