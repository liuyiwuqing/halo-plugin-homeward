package site.muyin.homeward.endpoint.routes;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;
import static site.muyin.homeward.endpoint.HomewardConsoleEndpoint.CONSOLE_TAG;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListResult;
import site.muyin.homeward.model.CaseApplicationRequest;
import site.muyin.homeward.scheme.CaseApplication;
import site.muyin.homeward.service.CaseApplicationService;

@Component
@RequiredArgsConstructor
public class CaseApplicationRoutes {
    private final CaseApplicationService service;
    public record ReviewRequest(String action, String note) { }

    public Mono<ServerResponse> config(ServerRequest request) {
        return service.enabled().flatMap(enabled -> ServerResponse.ok().header("Cache-Control", "no-store")
            .bodyValue(Map.of("enabled", enabled)));
    }

    public Mono<ServerResponse> submit(ServerRequest request) {
        return request.bodyToMono(CaseApplicationRequest.class)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求内容不能为空")))
            .flatMap(body -> service.submit(body, request))
            .flatMap(result -> ServerResponse.ok().header("Cache-Control", "no-store").bodyValue(result));
    }

    public RouterFunction<ServerResponse> consoleRoutes() {
        return SpringdocRouteBuilder.route()
            .GET("/list", this::list, builder -> builder
                .operationId("homeward-application-list").tag(CONSOLE_TAG).description("寻亲申请列表")
                .parameter(parameterBuilder().name("page").in(ParameterIn.QUERY)
                    .description("页码，从 1 开始").implementation(Integer.class))
                .parameter(parameterBuilder().name("size").in(ParameterIn.QUERY)
                    .description("每页数量，默认 20，最多 50").implementation(Integer.class))
                .parameter(parameterBuilder().name("status").in(ParameterIn.QUERY)
                    .description("PENDING、CONVERTING、ACCEPTED 或 REJECTED").implementation(String.class))
                .response(responseBuilder().implementation(ListResult.generateGenericClass(CaseApplication.class))))
            .POST("/{name}/review", this::review, builder -> builder
                .operationId("homeward-application-review").tag(CONSOLE_TAG)
                .description("审核申请并创建待核对草稿")
                .parameter(parameterBuilder().name("name").in(ParameterIn.PATH).required(true)
                    .implementation(String.class))
                .requestBody(requestBodyBuilder().required(true)
                    .content(contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE)
                        .schema(schemaBuilder().implementation(ReviewRequest.class))))
                .response(responseBuilder().implementation(CaseApplication.class)))
            .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        return Mono.defer(() -> service.list(number(request, "page", 1), number(request, "size", 20),
                request.queryParam("status").orElse(null)))
            .flatMap(result -> ServerResponse.ok().header("Cache-Control", "no-store").bodyValue(result));
    }

    private Mono<ServerResponse> review(ServerRequest request) {
        return request.principal()
            .filter(principal -> !(principal instanceof AnonymousAuthenticationToken)
                && (!(principal instanceof Authentication auth) || auth.isAuthenticated()))
            .filter(principal -> principal.getName() != null && !principal.getName().isBlank())
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录")))
            .flatMap(principal -> request.bodyToMono(ReviewRequest.class)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写审核结果")))
                .flatMap(body -> {
                    if (!"accept".equals(body.action()) && !"reject".equals(body.action())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "审核操作无效"));
                    }
                    return service.review(request.pathVariable("name"), "accept".equals(body.action()),
                        body.note(), principal.getName());
                }))
            .flatMap(result -> ServerResponse.ok().header("Cache-Control", "no-store").bodyValue(result));
    }

    private static int number(ServerRequest request, String name, int fallback) {
        try {
            return Integer.parseInt(request.queryParam(name).orElse(Integer.toString(fallback)));
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数无效");
        }
    }
}
