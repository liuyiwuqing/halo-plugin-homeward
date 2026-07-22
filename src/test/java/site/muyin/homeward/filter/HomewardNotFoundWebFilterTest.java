package site.muyin.homeward.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.homeward.setting.HomewardDisplaySetting;
import site.muyin.homeward.setting.HomewardGeneralSetting;

class HomewardNotFoundWebFilterTest {

    @Test
    void rendersHomewardPageForNotFoundAndKeepsNotFoundStatus() {
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        when(settingFetcher.fetch(HomewardGeneralSetting.GROUP, HomewardGeneralSetting.class))
            .thenReturn(Mono.just(HomewardGeneralSetting.defaults()));
        when(settingFetcher.fetch(HomewardDisplaySetting.GROUP, HomewardDisplaySetting.class))
            .thenReturn(Mono.just(HomewardDisplaySetting.defaults()));

        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/missing")
            .accept(MediaType.TEXT_HTML));
        var filter = new HomewardNotFoundWebFilter(settingFetcher);

        var chainCalls = new AtomicInteger();
        filter.filter(exchange, currentExchange -> {
            if (chainCalls.getAndIncrement() == 0) {
                return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
            }
            assertThat(currentExchange.getRequest().getPath().value()).isEqualTo("/homeward");
            var response = currentExchange.getResponse();
            response.setStatusCode(HttpStatus.OK);
            response.getHeaders().setContentType(MediaType.TEXT_HTML);
            var body = """
                <html><head>
                <link rel="stylesheet" href="/plugins/homeward/assets/static/homeward/homeward-public.css" />
                </head><body><main id="homeward-page-content"></main>
                <script>window.__HOMEWARD_PAGE__ = true;</script>
                <script defer src="/plugins/homeward/assets/static/homeward/homeward-public.js"></script>
                </body></html>
                """;
            return response.writeWith(Mono.just(response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8))));
        }).block(Duration.ofSeconds(1));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getBodyAsString().block(Duration.ofSeconds(1)))
            .contains("/plugins/homeward/assets/static/homeward/homeward-public.css")
            .contains("window.__HOMEWARD_PAGE__ = true")
            .contains("/plugins/homeward/assets/static/homeward/homeward-public.js")
            .contains("homeward-page-content");
        assertThat(chainCalls).hasValue(2);
        assertThat(exchange.getAttributes().get(HomewardNotFoundWebFilter.NOT_FOUND_ATTRIBUTE))
            .isEqualTo(true);
    }

    @Test
    void leavesSuccessfulHtmlResponseUntouched() {
        var settingFetcher = enabledSettingFetcher();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/post")
            .accept(MediaType.TEXT_HTML));

        writeHtmlResponse(
            new HomewardNotFoundWebFilter(settingFetcher),
            exchange,
            HttpStatus.OK
        );

        assertThat(exchange.getResponse().getBodyAsString().block(Duration.ofSeconds(1)))
            .isEqualTo("<html><head><title>404</title></head><body>missing</body></html>");
    }

    @Test
    void bypassesPluginAndApiPaths() {
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/apis/example/v1/missing")
            .accept(MediaType.TEXT_HTML));
        var notFound = new ResponseStatusException(HttpStatus.NOT_FOUND);

        assertThatThrownBy(() -> new HomewardNotFoundWebFilter(settingFetcher)
            .filter(exchange, currentExchange -> Mono.error(notFound))
            .block(Duration.ofSeconds(1)))
            .isSameAs(notFound);
        verify(settingFetcher, never()).fetch(
            HomewardGeneralSetting.GROUP,
            HomewardGeneralSetting.class
        );
    }

    @Test
    void respectsDisabled404InjectionSetting() {
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        var displaySetting = HomewardDisplaySetting.defaults();
        displaySetting.setInject404(false);
        when(settingFetcher.fetch(HomewardGeneralSetting.GROUP, HomewardGeneralSetting.class))
            .thenReturn(Mono.just(HomewardGeneralSetting.defaults()));
        when(settingFetcher.fetch(HomewardDisplaySetting.GROUP, HomewardDisplaySetting.class))
            .thenReturn(Mono.just(displaySetting));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/missing")
            .accept(MediaType.TEXT_HTML));
        var notFound = new ResponseStatusException(HttpStatus.NOT_FOUND);

        assertThatThrownBy(() -> new HomewardNotFoundWebFilter(settingFetcher)
            .filter(exchange, currentExchange -> Mono.error(notFound))
            .block(Duration.ofSeconds(1)))
            .isSameAs(notFound);

        assertThat(exchange.getAttributes().get(HomewardNotFoundWebFilter.NOT_FOUND_ATTRIBUTE))
            .isNull();
    }

    private ReactiveSettingFetcher enabledSettingFetcher() {
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        when(settingFetcher.fetch(HomewardGeneralSetting.GROUP, HomewardGeneralSetting.class))
            .thenReturn(Mono.just(HomewardGeneralSetting.defaults()));
        when(settingFetcher.fetch(HomewardDisplaySetting.GROUP, HomewardDisplaySetting.class))
            .thenReturn(Mono.just(HomewardDisplaySetting.defaults()));
        return settingFetcher;
    }

    private void writeHtmlResponse(HomewardNotFoundWebFilter filter,
                                   MockServerWebExchange exchange,
                                   HttpStatus status) {
        filter.filter(exchange, currentExchange -> {
            var response = currentExchange.getResponse();
            response.setStatusCode(status);
            response.getHeaders().setContentType(MediaType.TEXT_HTML);
            var body = "<html><head><title>404</title></head><body>missing</body></html>";
            return response.writeWith(Mono.just(response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8))));
        }).block(Duration.ofSeconds(1));
    }
}
