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
import site.muyin.homeward.endpoint.routes.DashboardRoutes;
import site.muyin.homeward.endpoint.routes.MissingPersonCaseRoutes;

@Component
@RequiredArgsConstructor
public class HomewardConsoleEndpoint implements CustomEndpoint {

    public static final String CONSOLE_GROUP_VERSION = "console.homeward.muyin.site/v1alpha1";

    public static final String CONSOLE_TAG = CONSOLE_GROUP_VERSION + "/Homeward";

    private final MissingPersonCaseRoutes caseRoutes;

    private final ClueReportRoutes clueRoutes;

    private final DashboardRoutes dashboardRoutes;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
            .nest(RequestPredicates.path("cases"), caseRoutes::consoleRoutes)
            .nest(RequestPredicates.path("clues"), clueRoutes::consoleRoutes)
            .nest(RequestPredicates.path("dashboard"), dashboardRoutes::consoleRoutes)
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(CONSOLE_GROUP_VERSION);
    }
}
