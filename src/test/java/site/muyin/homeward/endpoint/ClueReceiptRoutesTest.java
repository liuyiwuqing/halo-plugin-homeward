package site.muyin.homeward.endpoint;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import site.muyin.homeward.endpoint.routes.ClueReceiptRoutes;
import site.muyin.homeward.enums.ClueStatus;
import site.muyin.homeward.model.ClueReceiptVO;
import site.muyin.homeward.service.ClueReceiptService;
import site.muyin.homeward.support.ReceiptSupport;

class ClueReceiptRoutesTest {
    private final ClueReceiptService service = mock(ClueReceiptService.class);
    private final ClueReceiptRoutes routes = new ClueReceiptRoutes(service, new ReceiptSupport());
    private final WebTestClient client = WebTestClient.bindToRouterFunction(route(POST("/lookup"),
        routes::lookupForPublic)).build();

    @Test
    void validLookupHasNoStoreAndOnlyPublicReceiptFields() {
        when(service.lookup(anyString())).thenReturn(Mono.just(new ClueReceiptVO(ClueStatus.PENDING,
            Instant.parse("2026-10-11T01:00:00Z"), Instant.parse("2026-10-11T01:00:00Z"), "已收到")));
        client.post().uri("/lookup").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"receiptToken\":\"" + ReceiptSupport.generateToken() + "\"}").exchange()
            .expectStatus().isOk().expectHeader().valueEquals("Cache-Control", "no-store")
            .expectBody().jsonPath("$.status").isEqualTo("PENDING")
            .jsonPath("$.submittedAt").exists().jsonPath("$.updatedAt").exists()
            .jsonPath("$.receiptToken").doesNotExist().jsonPath("$.contact").doesNotExist();
    }

    @Test
    void malformedEmptyAndOversizedBodiesUseSame404AndNeverCallService() {
        for (String body : new String[] {"", "{bad", "{}", "{\"receiptToken\":123}",
            "{\"receiptToken\":\"bad\"}", "{\"receiptToken\":\"" + "a".repeat(2000) + "\"}"}) {
            client.post().uri("/lookup").contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .exchange().expectStatus().isNotFound()
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectBody().jsonPath("$.detail").isEqualTo("回执不存在，请核对凭证");
        }
        verifyNoInteractions(service);
    }

    @Test
    void missingReceiptAndDisabledServiceAreNotCacheable() {
        for (HttpStatus status : new HttpStatus[] {HttpStatus.NOT_FOUND, HttpStatus.SERVICE_UNAVAILABLE}) {
            when(service.lookup(anyString())).thenReturn(Mono.error(new ResponseStatusException(status, "暂不可用")));
            client.post().uri("/lookup").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"receiptToken\":\"" + ReceiptSupport.generateToken() + "\"}").exchange()
                .expectStatus().isEqualTo(status).expectHeader().valueEquals("Cache-Control", "no-store");
        }
    }

    @Test
    void invalidAttemptsAreRateLimitedAnd429IsNotCacheable() {
        for (int index = 0; index < 30; index++) {
            client.post().uri("/lookup").contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange().expectStatus().isNotFound();
        }
        client.post().uri("/lookup").contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange()
            .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
            .expectHeader().valueEquals("Cache-Control", "no-store");
        verifyNoInteractions(service);
    }
}
