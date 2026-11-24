package site.muyin.homeward.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import site.muyin.homeward.endpoint.routes.ClueReportRoutes;
import site.muyin.homeward.endpoint.routes.MissingPersonCaseRoutes;

class HomewardPublicEndpointTest {

    @Test
    void listUsesCollectionRootWithoutReservingAnExistingCaseSlug() {
        var caseRoutes = mock(MissingPersonCaseRoutes.class);
        when(caseRoutes.listForPublic(any()))
            .thenReturn(ServerResponse.ok().bodyValue(Map.of("route", "collection")));
        when(caseRoutes.detailForPublic(any())).thenAnswer(invocation -> {
            ServerRequest request = invocation.getArgument(0);
            return ServerResponse.ok().bodyValue(Map.of("slug", request.pathVariable("slug")));
        });
        var endpoint = new HomewardPublicEndpoint(caseRoutes, mock(ClueReportRoutes.class),
            mock(site.muyin.homeward.endpoint.routes.CaseApplicationRoutes.class),
            mock(site.muyin.homeward.endpoint.routes.ClueReceiptRoutes.class));
        var client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();

        client.get().uri("/cases?keyword=person&page=2&size=10").exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.route").isEqualTo("collection");
        client.get().uri("/cases/list").exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.slug").isEqualTo("list");
    }
}
