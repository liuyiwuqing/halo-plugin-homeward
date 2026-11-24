package site.muyin.homeward.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import site.muyin.homeward.endpoint.routes.ClueReportRoutes;
import site.muyin.homeward.enums.ClueStatus;
import site.muyin.homeward.model.ClueReportVO;
import site.muyin.homeward.model.ClueStatusUpdateRequest;
import site.muyin.homeward.service.ClueReportService;

class ClueStatusRouteTest {
    @Test
    void forgedBodyHandlerCannotReplaceAuthenticatedPrincipal() {
        var service = mock(ClueReportService.class);
        when(service.updateStatus(eq("clue-a"), any(), eq("operator-li")))
            .thenAnswer(invocation -> {
                ClueStatusUpdateRequest request = invocation.getArgument(1);
                assertThat(request.getStatus()).isEqualTo(ClueStatus.CONTACTED);
                var result = new ClueReportVO();
                result.setHandler(invocation.getArgument(2));
                return Mono.just(result);
            });
        var client = WebTestClient.bindToRouterFunction(new ClueReportRoutes(service).consoleRoutes())
            .webFilter((exchange, chain) -> chain.filter(exchange.mutate().principal(Mono.just(
                UsernamePasswordAuthenticationToken.authenticated("operator-li", "", List.of()))).build()))
            .build();
        client.post().uri("/clue-a/status").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"status\":\"CONTACTED\",\"handler\":\"admin\"}").exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.handler").isEqualTo("operator-li");
    }

    @Test
    void missingOrAnonymousPrincipalCannotProcessClues() {
        var service = mock(ClueReportService.class);
        var routes = new ClueReportRoutes(service).consoleRoutes();
        WebTestClient.bindToRouterFunction(routes).build().post().uri("/clue-a/status")
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"status\":\"CONTACTED\"}")
            .exchange().expectStatus().isUnauthorized();
        var client = WebTestClient.bindToRouterFunction(routes)
            .webFilter((exchange, chain) -> chain.filter(exchange.mutate().principal(Mono.just(
                new AnonymousAuthenticationToken("key", "anonymousUser", List.of(
                    new SimpleGrantedAuthority("ROLE_ANONYMOUS"))))).build())).build();
        client.post().uri("/clue-a/status").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"status\":\"CONTACTED\"}").exchange().expectStatus().isUnauthorized();
        verifyNoInteractions(service);
    }
}
