package site.muyin.homeward.endpoint;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import site.muyin.homeward.endpoint.routes.CaseApplicationRoutes;
import site.muyin.homeward.scheme.CaseApplication;
import site.muyin.homeward.service.CaseApplicationService;

class CaseApplicationRoutesTest {
    @Test
    void reviewUsesTheAuthenticatedOperator() {
        var service = mock(CaseApplicationService.class);
        when(service.review(eq("application-a"), eq(true), eq("已核实"), eq("operator-li")))
            .thenReturn(Mono.just(new CaseApplication()));
        var client = WebTestClient.bindToRouterFunction(new CaseApplicationRoutes(service).consoleRoutes())
            .webFilter((exchange, chain) -> chain.filter(exchange.mutate().principal(Mono.just(
                UsernamePasswordAuthenticationToken.authenticated("operator-li", "", List.of()))).build()))
            .build();

        client.post().uri("/application-a/review").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"action\":\"accept\",\"note\":\"已核实\"}").exchange().expectStatus().isOk();

        verify(service).review("application-a", true, "已核实", "operator-li");
    }

    @Test
    void missingAnonymousAndUnauthenticatedPrincipalsCannotReview() {
        var service = mock(CaseApplicationService.class);
        var routes = new CaseApplicationRoutes(service).consoleRoutes();
        WebTestClient.bindToRouterFunction(routes).build().post().uri("/application-a/review")
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"action\":\"accept\"}")
            .exchange().expectStatus().isUnauthorized();

        var anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
            List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        var unauthenticated = UsernamePasswordAuthenticationToken.unauthenticated("operator-li", "");
        for (var principal : List.of(anonymous, unauthenticated)) {
            var client = WebTestClient.bindToRouterFunction(routes)
                .webFilter((exchange, chain) -> chain.filter(exchange.mutate()
                    .principal(Mono.just(principal)).build())).build();
            client.post().uri("/application-a/review").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"action\":\"accept\"}").exchange().expectStatus().isUnauthorized();
        }
        verifyNoInteractions(service);
    }
}
