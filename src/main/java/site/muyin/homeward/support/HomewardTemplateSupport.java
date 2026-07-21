package site.muyin.homeward.support;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Map;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.context.IWebContext;

public final class HomewardTemplateSupport {

    private HomewardTemplateSupport() {
    }

    public static boolean shouldSkipPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        return path.startsWith("/console")
            || path.startsWith("/uc")
            || path.startsWith("/apis")
            || path.startsWith("/api")
            || path.startsWith("/plugins");
    }

    public static String requestPath(ITemplateContext context) {
        if (!(context instanceof IWebContext webContext)) {
            return "/";
        }
        try {
            String requestUrl = webContext.getExchange().getRequest().getRequestURL();
            String path = URI.create(requestUrl).getRawPath();
            return path == null || path.isBlank() ? "/" : path;
        } catch (Exception ignored) {
            return "/";
        }
    }

    public static boolean isNotFoundContext(ITemplateContext context) {
        Object error = context.getVariable("error");
        Object status = extractStatus(error);
        if (status instanceof Number number) {
            return number.intValue() == 404;
        }
        return status != null && "404".equals(status.toString());
    }

    private static Object extractStatus(Object error) {
        if (error == null) {
            return null;
        }
        if (error instanceof Map<?, ?> map) {
            return map.get("status");
        }
        try {
            Method method = error.getClass().getMethod("getStatus");
            return method.invoke(error);
        } catch (Exception ignored) {
            return null;
        }
    }
}
