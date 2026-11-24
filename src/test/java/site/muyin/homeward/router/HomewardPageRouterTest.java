package site.muyin.homeward.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.RenderingResponse;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import run.halo.app.infra.ExternalUrlSupplier;
import run.halo.app.theme.TemplateNameResolver;
import site.muyin.homeward.model.MissingPersonCasePublicVO;
import site.muyin.homeward.service.MissingPersonCaseService;

class HomewardPageRouterTest {

    @Test
    void standaloneCaseLinkMatchesAnHtmlPageRoute() {
        var resolver = mock(TemplateNameResolver.class);
        when(resolver.resolveTemplateNameOrDefault(any(), eq("homeward-case")))
            .thenReturn(Mono.just("homeward-case"));
        var router = new HomewardPageRouter(resolver, mock(MissingPersonCaseService.class),
            mock(ExternalUrlSupplier.class), mock(HomewardReceiptPage.class));
        var exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("https://example.test/homeward/cases/person-a"));
        var request = ServerRequest.create(exchange, HandlerStrategies.withDefaults().messageReaders());

        assertThat(router.homewardPageRouterFunction().route(request).block(Duration.ofSeconds(2)))
            .as("a shared case URL must resolve independently of the list page")
            .isNotNull();
    }

    @Test
    void sharedPageRendersPublicFactsAndSafeUrlsWithoutQueryInCanonicalUrl() throws Exception {
        var resolver = mock(TemplateNameResolver.class);
        when(resolver.resolveTemplateNameOrDefault(any(), eq("homeward-case")))
            .thenReturn(Mono.just("homeward-case"));
        var service = mock(MissingPersonCaseService.class);
        var item = new MissingPersonCasePublicVO();
        item.setSlug("person-a");
        item.setTitle("测试案例 A");
        item.setDescription("完整公开描述");
        item.setCoverImage("javascript:alert(1)");
        item.setGallery(List.of("/photos/a.jpg", "https://images.test/b.jpg", "//evil.test/x",
            "https://name:password@example.test/x", "data:text/html,test"));
        item.setSourceUrl("https://source.test/case-a");
        item.setPublishedAt(Instant.parse("2026-10-11T03:00:00Z"));
        when(service.getPublishedBySlug("person-a")).thenReturn(Mono.just(item));
        var externalUrl = mock(ExternalUrlSupplier.class);
        when(externalUrl.getURL(any())).thenReturn(URI.create("https://example.test/").toURL());

        var response = (RenderingResponse) handle(new HomewardPageRouter(resolver, service, externalUrl, mock(HomewardReceiptPage.class)),
            "/homeward/cases/person-a?keyword=ignored");

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.name()).isEqualTo("homeward-case");
        assertThat(response.model()).containsEntry("caseData", item)
            .containsEntry("caseUrl", "https://example.test/homeward/cases/person-a")
            .containsEntry("safeCoverImage", "")
            .containsEntry("safeGallery", List.of("/photos/a.jpg", "https://images.test/b.jpg"))
            .containsEntry("safeSourceUrl", "https://source.test/case-a")
            .containsEntry("publishedDateText", "2026-10-11 03:00 UTC");
        assertThat(response.headers().getCacheControl()).isEqualTo("no-store");
    }

    @Test
    void withdrawnOrDisabledCaseGetsAnExplicitUnavailablePage() {
        for (var status : List.of(HttpStatus.NOT_FOUND, HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.GONE)) {
            var resolver = mock(TemplateNameResolver.class);
            when(resolver.resolveTemplateNameOrDefault(any(), eq("homeward-unavailable")))
                .thenReturn(Mono.just("homeward-unavailable"));
            var service = mock(MissingPersonCaseService.class);
            when(service.getPublishedBySlug("person-a"))
                .thenReturn(Mono.error(new ResponseStatusException(status)));

            var response = (RenderingResponse) handle(new HomewardPageRouter(resolver, service,
                mock(ExternalUrlSupplier.class), mock(HomewardReceiptPage.class)), "/homeward/cases/person-a");

            assertThat(response.statusCode()).isEqualTo(status);
            assertThat(response.name()).isEqualTo("homeward-unavailable");
            assertThat(response.model()).doesNotContainKey("caseData");
        }
    }

    private ServerResponse handle(HomewardPageRouter router, String path) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path));
        var request = ServerRequest.create(exchange, HandlerStrategies.withDefaults().messageReaders());
        return router.homewardPageRouterFunction().route(request)
            .flatMap(handler -> handler.handle(request)).block(Duration.ofSeconds(2));
    }
}
