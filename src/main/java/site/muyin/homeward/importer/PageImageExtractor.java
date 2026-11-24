package site.muyin.homeward.importer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Extracts displayable photo URLs from an imported web page.
 *
 * <p>This class only resolves addresses. It never downloads content, inspects image bytes, or
 * guesses the identity of the people in a photo.
 */
public final class PageImageExtractor {

    public static final int MAX_IMAGES = 20;

    private static final int MIN_VISIBLE_DIMENSION = 80;

    private static final String[] IMAGE_ATTRIBUTES = {
        "data-original", "data-src", "data-lazy-src", "data-echo", "src"
    };

    private static final String[] META_SELECTORS = {
        "meta[property=og:image]", "meta[name=twitter:image]", "meta[property=twitter:image]"
    };

    private static final Pattern DECORATIVE_NAME = Pattern.compile(
        "(?i)(^|[^a-z])(logo|icon|avatar|emoji|emoticon|blank|spacer|loading|placeholder"
            + "|pixel|1x1|qrcode|wechat|share|button|background|sprite)([^a-z]|$)"
            + "|图标|头像|二维码|占位|加载中|背景图");

    private static final Pattern IMAGE_PATH = Pattern.compile(
        "(?i)\\.(jpe?g|png|webp|gif|avif|bmp)$");

    private PageImageExtractor() {
    }

    static List<String> extract(Document document, String sourceUrl) {
        Set<String> urls = new LinkedHashSet<>();
        for (String selector : META_SELECTORS) {
            for (Element meta : document.select(selector)) {
                add(urls, resolve(meta.attr("content"), meta, sourceUrl), meta);
            }
        }
        for (Element image : document.select("img")) {
            if (isDecorative(image, hint(image))) {
                continue;
            }
            String srcset = image.attr("srcset");
            if (!srcset.isBlank()) {
                String largest = null;
                for (String entry : srcset.split(",")) {
                    String candidate = entry.trim().split("\\s+", 2)[0];
                    String url = resolve(candidate, image, sourceUrl);
                    if (url != null) {
                        largest = url;
                    }
                }
                if (largest != null) {
                    urls.add(largest);
                    continue;
                }
            }
            for (String attribute : IMAGE_ATTRIBUTES) {
                String url = resolve(image.attr(attribute), image, sourceUrl);
                if (url != null) {
                    urls.add(url);
                    break;
                }
            }
        }
        for (Element link : document.select("a[href]")) {
            if (link.selectFirst("img") == null && !hint(link).contains("gallery")
                && !hint(link).contains("photo") && !hint(link).contains("album")) {
                continue;
            }
            String url = resolve(link.attr("href"), link, sourceUrl);
            if (url != null && isImagePath(url) && !DECORATIVE_NAME.matcher(url).find()) {
                urls.add(url);
            }
        }
        return urls.stream().limit(MAX_IMAGES).toList();
    }

    /** Resolves an imported address against the source page, keeping only public HTTP(S) URLs. */
    public static String resolve(String raw, Element element, String sourceUrl) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("data:") || lower.startsWith("blob:")
            || lower.startsWith("javascript:") || lower.startsWith("about:")
            || lower.startsWith("mailto:")) {
            return null;
        }
        String base = baseUri(element, sourceUrl);
        try {
            URI uri = base == null ? URI.create(value) : URI.create(base).resolve(value);
            return canonical(uri);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    public static String resolve(String raw, String sourceUrl) {
        return resolve(raw, null, sourceUrl);
    }

    private static void add(Set<String> urls, String url, Element element) {
        if (url == null || urls.contains(url) || isDecorative(element, url)) {
            return;
        }
        urls.add(url);
    }

    private static String baseUri(Element element, String sourceUrl) {
        if (element != null && element.ownerDocument() != null) {
            Element base = element.ownerDocument().selectFirst("base[href]");
            if (base != null) {
                String absoluteBase = resolve(base.attr("href"), null, sourceUrl);
                if (absoluteBase != null) {
                    return absoluteBase;
                }
            }
        }
        return sourceUrl;
    }

    private static String canonical(URI uri) {
        String scheme = uri.getScheme();
        if (uri.getHost() == null || uri.getUserInfo() != null
            || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            return null;
        }
        try {
            return new URI(scheme.toLowerCase(Locale.ROOT), null, uri.getHost(), uri.getPort(),
                uri.getPath(), uri.getQuery(), null).toString();
        } catch (URISyntaxException error) {
            return null;
        }
    }

    private static boolean isDecorative(Element element, String hint) {
        if (hint != null && DECORATIVE_NAME.matcher(hint).find()) {
            return true;
        }
        if (element != null && "img".equalsIgnoreCase(element.tagName())) {
            int width = dimension(element.attr("width"));
            int height = dimension(element.attr("height"));
            return (width > 0 && width < MIN_VISIBLE_DIMENSION)
                || (height > 0 && height < MIN_VISIBLE_DIMENSION);
        }
        return false;
    }

    private static int dimension(String value) {
        try {
            String digits = value == null ? "" : value.trim().replace("px", "");
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    private static String hint(Element element) {
        var hint = new StringBuilder(element.className()).append(' ').append(element.id())
            .append(' ').append(element.attr("alt"));
        for (String attribute : IMAGE_ATTRIBUTES) {
            hint.append(' ').append(element.attr(attribute));
        }
        return hint.toString();
    }

    private static boolean isImagePath(String url) {
        int query = url.indexOf('?');
        int fragment = url.indexOf('#');
        int end = query < 0 ? url.length() : query;
        if (fragment >= 0 && fragment < end) {
            end = fragment;
        }
        return IMAGE_PATH.matcher(url.substring(0, end)).find();
    }
}
