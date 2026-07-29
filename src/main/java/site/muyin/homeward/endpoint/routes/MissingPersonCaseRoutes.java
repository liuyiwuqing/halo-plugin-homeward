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
import site.muyin.homeward.enums.CaseStatus;
import site.muyin.homeward.importer.CaseImportService;
import site.muyin.homeward.model.CaseImportPreview;
import site.muyin.homeward.model.CaseImportRequest;
import site.muyin.homeward.model.MissingPersonCasePublicVO;
import site.muyin.homeward.model.MissingPersonCaseVO;
import site.muyin.homeward.model.WidgetResponse;
import site.muyin.homeward.query.MissingPersonCaseQuery;
import site.muyin.homeward.scheme.MissingPersonCase;
import site.muyin.homeward.service.MissingPersonCaseService;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static site.muyin.homeward.endpoint.HomewardConsoleEndpoint.CONSOLE_TAG;

@Component
@RequiredArgsConstructor
public class MissingPersonCaseRoutes {

    private final MissingPersonCaseService service;

    private final CaseImportService caseImportService;

    public RouterFunction<ServerResponse> consoleRoutes() {
        return SpringdocRouteBuilder.route()
            .GET("/list", this::listForConsole, builder -> {
                builder.operationId("homeward-case-list-for-console")
                    .description("寻亲案例列表")
                    .tag(CONSOLE_TAG)
                    .response(responseBuilder()
                        .implementation(ListResult.generateGenericClass(MissingPersonCaseVO.class)));
                MissingPersonCaseQuery.buildParameters(builder);
            })
            .GET("/{name}", this::getForConsole, builder -> builder
                .operationId("homeward-case-detail-for-console")
                .description("寻亲案例详情")
                .tag(CONSOLE_TAG)
                .parameter(parameterBuilder().name("name").in(ParameterIn.PATH).required(true)
                    .implementation(String.class))
                .response(responseBuilder().implementation(MissingPersonCaseVO.class)))
            .POST("/create", this::createForConsole, builder -> builder
                .operationId("homeward-case-create-for-console")
                .description("创建寻亲案例")
                .tag(CONSOLE_TAG)
                .requestBody(requestBodyBuilder().required(true)
                    .content(contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE)
                        .schema(Builder.schemaBuilder().implementation(MissingPersonCase.class))))
                .response(responseBuilder().implementation(MissingPersonCaseVO.class)))
            .POST("/update", this::updateForConsole, builder -> builder
                .operationId("homeward-case-update-for-console")
                .description("更新寻亲案例")
                .tag(CONSOLE_TAG)
                .requestBody(requestBodyBuilder().required(true)
                    .content(contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE)
                        .schema(Builder.schemaBuilder().implementation(MissingPersonCase.class))))
                .response(responseBuilder().implementation(MissingPersonCaseVO.class)))
            .POST("/import/preview", this::previewImportForConsole, builder -> builder
                .operationId("homeward-case-import-preview-for-console")
                .description("从公网网页或粘贴正文解析寻亲案例草稿，不直接保存")
                .tag(CONSOLE_TAG)
                .requestBody(requestBodyBuilder().required(true)
                    .content(contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE)
                        .schema(Builder.schemaBuilder().implementation(CaseImportRequest.class))))
                .response(responseBuilder().implementation(CaseImportPreview.class)))
            .POST("/{name}/publish", request -> status(request, CaseStatus.PUBLISHED), builder -> builder
                .operationId("homeward-case-publish-for-console")
                .description("发布寻亲案例")
                .tag(CONSOLE_TAG))
            .POST("/{name}/mark-found", request -> status(request, CaseStatus.FOUND), builder -> builder
                .operationId("homeward-case-mark-found-for-console")
                .description("标记已找到")
                .tag(CONSOLE_TAG))
            .POST("/{name}/archive", request -> status(request, CaseStatus.ARCHIVED), builder -> builder
                .operationId("homeward-case-archive-for-console")
                .description("归档寻亲案例")
                .tag(CONSOLE_TAG))
            .DELETE("/{name}/delete", this::deleteForConsole, builder -> builder
                .operationId("homeward-case-delete-for-console")
                .description("删除寻亲案例")
                .tag(CONSOLE_TAG))
            .build();
    }

    public Mono<ServerResponse> widget(ServerRequest request) {
        boolean publicPage = request.queryParam("surface")
            .map("page"::equalsIgnoreCase)
            .orElse(false);
        return service.widget(publicPage)
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    public Mono<ServerResponse> detailForPublic(ServerRequest request) {
        return service.getPublishedBySlug(request.pathVariable("slug"))
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private Mono<ServerResponse> listForConsole(ServerRequest request) {
        return service.listForConsole(new MissingPersonCaseQuery(request.exchange()))
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private Mono<ServerResponse> getForConsole(ServerRequest request) {
        return service.getForConsole(request.pathVariable("name"))
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private Mono<ServerResponse> createForConsole(ServerRequest request) {
        return request.bodyToMono(MissingPersonCase.class)
            .flatMap(service::create)
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private Mono<ServerResponse> updateForConsole(ServerRequest request) {
        return request.bodyToMono(MissingPersonCase.class)
            .flatMap(service::update)
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private Mono<ServerResponse> previewImportForConsole(ServerRequest request) {
        return request.bodyToMono(CaseImportRequest.class)
            .flatMap(caseImportService::preview)
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private Mono<ServerResponse> status(ServerRequest request, CaseStatus status) {
        return service.updateStatus(request.pathVariable("name"), status)
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private Mono<ServerResponse> deleteForConsole(ServerRequest request) {
        return service.delete(request.pathVariable("name"))
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }
}
