package site.muyin.homeward.endpoint;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListResult;
import site.muyin.homeward.endpoint.routes.CaseApplicationRoutes;
import site.muyin.homeward.endpoint.routes.ClueReceiptRoutes;
import site.muyin.homeward.endpoint.routes.ClueReportRoutes;
import site.muyin.homeward.endpoint.routes.MissingPersonCaseRoutes;
import site.muyin.homeward.model.CaseApplicationRequest;
import site.muyin.homeward.model.ClueReceiptRequest;
import site.muyin.homeward.model.ClueReceiptVO;
import site.muyin.homeward.model.MissingPersonCasePublicVO;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

@Component
@RequiredArgsConstructor
public class HomewardPublicEndpoint implements CustomEndpoint {

    public static final String PUBLIC_GROUP_VERSION = "public.homeward.muyin.site/v1alpha1";

    public static final String PUBLIC_TAG = PUBLIC_GROUP_VERSION + "/Homeward";

    private final MissingPersonCaseRoutes caseRoutes;

    private final ClueReportRoutes clueRoutes;

    private final CaseApplicationRoutes applicationRoutes;

    private final ClueReceiptRoutes receiptRoutes;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
            .GET("applications/config", applicationRoutes::config, builder -> builder
                .operationId("homeward-application-config").tag(PUBLIC_TAG).description("是否开放寻亲申请"))
            .POST("applications", applicationRoutes::submit, builder -> builder
                .operationId("homeward-application-submit").tag(PUBLIC_TAG).description("提交待人工审核的寻亲申请")
                .requestBody(requestBodyBuilder().required(true)
                    .content(contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE)
                        .schema(schemaBuilder().implementation(CaseApplicationRequest.class)))))
            .POST("clue-receipts/lookup", receiptRoutes::lookupForPublic, builder -> builder
                .operationId("homeward-clue-receipt-lookup").tag(PUBLIC_TAG).description("凭回执查看线索处理进度")
                .requestBody(requestBodyBuilder().required(true)
                    .content(contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE)
                        .schema(schemaBuilder().implementation(ClueReceiptRequest.class))))
                .response(responseBuilder().implementation(ClueReceiptVO.class)))
            .GET("widget", caseRoutes::widget, builder -> builder
                .operationId("homeward-widget-for-public")
                .description("获取 404 公益展示数据")
                .tag(PUBLIC_TAG))
            .GET("cases", caseRoutes::listForPublic, builder -> builder
                .operationId("homeward-case-list-for-public")
                .description("分页搜索已发布寻亲案例，仅接受关键词和分页参数")
                .tag(PUBLIC_TAG)
                .parameter(parameterBuilder().in(ParameterIn.QUERY).name("keyword")
                    .description("姓名、标题或失踪地点，最多 120 个字符").implementation(String.class))
                .parameter(parameterBuilder().in(ParameterIn.QUERY).name("page")
                    .description("页码，从 1 开始").implementation(Integer.class))
                .parameter(parameterBuilder().in(ParameterIn.QUERY).name("size")
                    .description("每页数量，默认 20，最多 50").implementation(Integer.class))
                .response(responseBuilder()
                    .implementation(ListResult.generateGenericClass(MissingPersonCasePublicVO.class))))
            .nest(RequestPredicates.path("cases"), () -> SpringdocRouteBuilder.route()
                .GET("/{slug}", caseRoutes::detailForPublic, builder -> builder
                    .operationId("homeward-case-detail-for-public")
                    .description("获取公开寻亲案例详情")
                    .tag(PUBLIC_TAG))
                .POST("/{slug}/clues", clueRoutes::submitForPublic, builder -> builder
                    .operationId("homeward-clue-submit-for-public")
                    .description("提交寻亲线索")
                    .tag(PUBLIC_TAG))
                .build())
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(PUBLIC_GROUP_VERSION);
    }
}
