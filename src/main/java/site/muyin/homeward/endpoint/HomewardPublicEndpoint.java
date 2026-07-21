package site.muyin.homeward.endpoint;

import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import site.muyin.homeward.endpoint.routes.ClueReportRoutes;
import site.muyin.homeward.endpoint.routes.MissingPersonCaseRoutes;

@Component
@RequiredArgsConstructor
public class HomewardPublicEndpoint implements CustomEndpoint {

    public static final String PUBLIC_GROUP_VERSION = "public.homeward.muyin.site/v1alpha1";

    public static final String PUBLIC_TAG = PUBLIC_GROUP_VERSION + "/Homeward";

    private final MissingPersonCaseRoutes caseRoutes;

    private final ClueReportRoutes clueRoutes;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
            .GET("widget", caseRoutes::widget, builder -> builder
                .operationId("homeward-widget-for-public")
                .description("获取 404 公益展示数据")
                .tag(PUBLIC_TAG))
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
