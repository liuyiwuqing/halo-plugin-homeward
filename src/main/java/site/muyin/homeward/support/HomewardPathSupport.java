package site.muyin.homeward.support;

public final class HomewardPathSupport {

    private HomewardPathSupport() {
    }

    public static boolean shouldSkip(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        return path.startsWith("/console")
            || path.startsWith("/uc")
            || path.startsWith("/apis")
            || path.startsWith("/api")
            || path.startsWith("/plugins");
    }
}
