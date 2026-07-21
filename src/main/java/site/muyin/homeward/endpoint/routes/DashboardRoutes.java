package site.muyin.homeward.endpoint.routes;

import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import site.muyin.homeward.enums.CaseStatus;
import site.muyin.homeward.enums.ClueStatus;
import site.muyin.homeward.model.DashboardStats;
import site.muyin.homeward.service.ClueReportService;
import site.muyin.homeward.service.MissingPersonCaseService;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static site.muyin.homeward.endpoint.HomewardConsoleEndpoint.CONSOLE_TAG;

@Component
@RequiredArgsConstructor
public class DashboardRoutes {

    private final MissingPersonCaseService caseService;

    private final ClueReportService clueService;

    public RouterFunction<ServerResponse> consoleRoutes() {
        return SpringdocRouteBuilder.route()
            .GET("/stats", this::stats, builder -> builder
                .operationId("homeward-dashboard-stats-for-console")
                .description("归航寻亲统计")
                .tag(CONSOLE_TAG)
                .response(responseBuilder().implementation(DashboardStats.class)))
            .build();
    }

    private Mono<ServerResponse> stats(ServerRequest request) {
        return Mono.zip(
                caseService.countAll(),
                caseService.countByStatus(CaseStatus.PUBLISHED),
                caseService.countByStatus(CaseStatus.FOUND),
                clueService.countByStatus(ClueStatus.PENDING)
            )
            .map(tuple -> DashboardStats.builder()
                .totalCases(tuple.getT1())
                .publishedCases(tuple.getT2())
                .foundCases(tuple.getT3())
                .pendingClues(tuple.getT4())
                .build())
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }
}
