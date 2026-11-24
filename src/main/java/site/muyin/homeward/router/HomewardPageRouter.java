package site.muyin.homeward.router;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import run.halo.app.infra.ExternalUrlSupplier;
import run.halo.app.theme.TemplateNameResolver;
import run.halo.app.theme.router.ModelConst;
import site.muyin.homeward.filter.HomewardNotFoundWebFilter;
import site.muyin.homeward.model.MissingPersonCasePublicVO;
import site.muyin.homeward.service.MissingPersonCaseService;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class HomewardPageRouter {

    private final TemplateNameResolver templateNameResolver;

    private final MissingPersonCaseService caseService;

    private final ExternalUrlSupplier externalUrlSupplier;

    private final HomewardReceiptPage receiptPage;

    @Bean
    RouterFunction<ServerResponse> homewardPageRouterFunction() {
        return route(GET("/homeward"), this::renderHomewardPage)
            .andRoute(GET("/homeward/apply"), this::renderApplicationPage)
            .andRoute(GET("/homeward/receipts"), receiptPage::render)
            .andRoute(GET("/homeward/cases/{slug}"), this::renderCasePage);
    }

    private Mono<ServerResponse> renderApplicationPage(ServerRequest request) {
        return templateNameResolver.resolveTemplateNameOrDefault(request.exchange(), "homeward-application")
            .flatMap(template -> ServerResponse.ok().contentType(MediaType.TEXT_HTML)
                .header("Cache-Control", "no-store")
                .render(template, Map.of(ModelConst.TEMPLATE_ID, "plugin:homeward:application")));
    }

    private Mono<ServerResponse> renderHomewardPage(ServerRequest request) {
        boolean notFound = Boolean.TRUE.equals(
            request.exchange().getAttribute(HomewardNotFoundWebFilter.NOT_FOUND_ATTRIBUTE)
        );
        return templateNameResolver.resolveTemplateNameOrDefault(request.exchange(), "homeward")
            .flatMap(template -> ServerResponse.status(
                    notFound ? HttpStatus.NOT_FOUND : HttpStatus.OK
                )
                .contentType(MediaType.TEXT_HTML)
                .render(template, Map.of(
                    "homewardApi", "/apis/public.homeward.muyin.site/v1alpha1",
                    "homeward404", notFound,
                    ModelConst.TEMPLATE_ID, "plugin:homeward:list"
                )));
    }

    private Mono<ServerResponse> renderCasePage(ServerRequest request) {
        return caseService.getPublishedBySlug(request.pathVariable("slug"))
            .flatMap(item -> templateNameResolver
                .resolveTemplateNameOrDefault(request.exchange(), "homeward-case")
                .flatMap(template -> ServerResponse.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .header("Cache-Control", "no-store")
                    .render(template, caseModel(request, item))))
            .onErrorResume(ResponseStatusException.class, error -> {
                int status = error.getStatusCode().value();
                if (status != 404 && status != 503 && status != 410) {
                    return Mono.error(error);
                }
                return templateNameResolver
                    .resolveTemplateNameOrDefault(request.exchange(), "homeward-unavailable")
                    .flatMap(template -> ServerResponse.status(error.getStatusCode())
                        .contentType(MediaType.TEXT_HTML)
                        .header("Cache-Control", "no-store")
                        .render(template, Map.of(
                            "pageTitle", status == 410 ? "这份寻亲信息已结案" : status == 404 ? "案例暂不可查看" : "寻亲服务暂未开放",
                            "pageMessage", status == 410 ? "该案例已标记为已找到，原资料已停止展示。感谢每一位提供线索和帮助转发的人，请停止传播旧海报。" : status == 404
                                ? "该案例不存在或已停止公开展示，请返回案例列表查看其他公开信息。"
                                : "请稍后再试。",
                            ModelConst.TEMPLATE_ID, "plugin:homeward:unavailable"
                        )));
            });
    }

    private Map<String, Object> caseModel(ServerRequest request, MissingPersonCasePublicVO item) {
        var model = new HashMap<String, Object>();
        String baseUrl = externalUrlSupplier.getURL(request.exchange().getRequest()).toString();
        String caseUrl = UriComponentsBuilder.fromUriString(baseUrl)
            .replacePath("/homeward/cases/{slug}").replaceQuery(null).fragment(null)
            .buildAndExpand(item.getSlug()).encode().toUriString();
        model.put("caseData", item);
        model.put("caseUrl", caseUrl);
        model.put("homeward404", false);
        model.put("safeCoverImage", safePublicUrl(item.getCoverImage()));
        String coverImage = safePublicUrl(item.getCoverImage());
        model.put("shareImageUrl", coverImage.isEmpty() ? ""
            : URI.create(caseUrl).resolve(coverImage).toString());
        model.put("safeGallery", item.getGallery() == null ? List.of()
            : item.getGallery().stream().map(HomewardPageRouter::safePublicUrl)
                .filter(value -> !value.isEmpty()).distinct().toList());
        model.put("safeSourceUrl", safePublicUrl(item.getSourceUrl()));
        model.put("publishedDateText", dateText(item.getPublishedAt()));
        model.put("updatedDateText", dateText(item.getUpdatedAt()));
        model.put(ModelConst.TEMPLATE_ID, "plugin:homeward:case");
        return model;
    }

    private static String dateText(Instant instant) {
        return instant == null ? "" : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC).format(instant);
    }

    /** Display imported links only as web URLs or local absolute paths. */
    private static String safePublicUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String text = value.trim();
        if (text.contains("\\") || text.chars().anyMatch(Character::isISOControl)) {
            return "";
        }
        try {
            URI uri = URI.create(text);
            if (text.startsWith("/") && !text.startsWith("//")) {
                return text;
            }
            if (("https".equalsIgnoreCase(uri.getScheme())
                || "http".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null && uri.getUserInfo() == null) {
                return text;
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid imported URLs remain visible as plain source information, never links.
        }
        return "";
    }
}
