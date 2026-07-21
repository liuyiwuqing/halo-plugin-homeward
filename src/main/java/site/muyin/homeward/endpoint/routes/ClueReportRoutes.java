package site.muyin.homeward.endpoint.routes;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.fn.builders.schema.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListResult;
import site.muyin.homeward.model.ClueReportVO;
import site.muyin.homeward.model.ClueStatusUpdateRequest;
import site.muyin.homeward.model.ClueSubmitRequest;
import site.muyin.homeward.query.ClueReportQuery;
import site.muyin.homeward.service.ClueReportService;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static site.muyin.homeward.endpoint.HomewardConsoleEndpoint.CONSOLE_TAG;

@Component
@RequiredArgsConstructor
public class ClueReportRoutes {

    private final ClueReportService service;

    public RouterFunction<ServerResponse> consoleRoutes() {
        return SpringdocRouteBuilder.route()
            .GET("/list", this::listForConsole, builder -> {
                builder.operationId("homeward-clue-list-for-console")
                    .description("线索列表")
                    .tag(CONSOLE_TAG)
                    .response(responseBuilder()
                        .implementation(ListResult.generateGenericClass(ClueReportVO.class)));
                ClueReportQuery.buildParameters(builder);
            })
            .GET("/{name}", this::getForConsole, builder -> builder
                .operationId("homeward-clue-detail-for-console")
                .description("线索详情")
                .tag(CONSOLE_TAG)
                .parameter(parameterBuilder().name("name").in(ParameterIn.PATH).required(true)
                    .implementation(String.class))
                .response(responseBuilder().implementation(ClueReportVO.class)))
            .POST("/{name}/status", this::updateStatusForConsole, builder -> builder
                .operationId("homeward-clue-status-update-for-console")
                .description("更新线索状态")
                .tag(CONSOLE_TAG)
                .requestBody(requestBodyBuilder().required(true)
                    .content(contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE)
                        .schema(Builder.schemaBuilder().implementation(ClueStatusUpdateRequest.class)))))
            .DELETE("/{name}/delete", this::deleteForConsole, builder -> builder
                .operationId("homeward-clue-delete-for-console")
                .description("删除线索")
                .tag(CONSOLE_TAG))
            .build();
    }

    public Mono<ServerResponse> submitForPublic(ServerRequest request) {
        return request.bodyToMono(ClueSubmitRequest.class)
            .flatMap(body -> service.submit(request.pathVariable("slug"), body, request))
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private Mono<ServerResponse> listForConsole(ServerRequest request) {
        return service.listForConsole(new ClueReportQuery(request.exchange()))
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private Mono<ServerResponse> getForConsole(ServerRequest request) {
        return service.getForConsole(request.pathVariable("name"))
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private Mono<ServerResponse> updateStatusForConsole(ServerRequest request) {
        return request.bodyToMono(ClueStatusUpdateRequest.class)
            .flatMap(body -> service.updateStatus(request.pathVariable("name"), body))
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private Mono<ServerResponse> deleteForConsole(ServerRequest request) {
        return service.delete(request.pathVariable("name"))
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }
}
