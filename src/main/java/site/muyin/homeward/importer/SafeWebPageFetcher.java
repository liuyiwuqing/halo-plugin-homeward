package site.muyin.homeward.importer;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class SafeWebPageFetcher implements WebPageFetcher {

    private static final int MAX_RESPONSE_BYTES = 1_000_000;

    private static final int MAX_REDIRECTS = 3;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);

    private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);

    private static final Pattern CHARSET_PATTERN = Pattern.compile(
        "(?i)charset\\s*=\\s*[\\\"']?([a-z0-9._-]+)");

    private final HttpClient httpClient;

    public SafeWebPageFetcher() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    @Override
    public Mono<FetchedWebPage> fetch(String sourceUrl) {
        return Mono.fromCallable(() -> fetchBlocking(parseUri(sourceUrl), 0))
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorMap(HttpTimeoutException.class,
                error -> upstreamError(HttpStatus.GATEWAY_TIMEOUT, "读取来源网页超时", error))
            .onErrorMap(IOException.class,
                error -> upstreamError(HttpStatus.BAD_GATEWAY, "无法读取来源网页", error));
    }

    private FetchedWebPage fetchBlocking(URI target, int redirects)
        throws IOException, InterruptedException {
        URI safeTarget = validatePublicTarget(target);
        var request = HttpRequest.newBuilder(safeTarget)
            .GET()
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.8")
            .header("User-Agent", "Halo-Homeward-Importer/1.0")
            .build();
        HttpResponse<InputStream> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            int status = response.statusCode();
            if (REDIRECT_STATUSES.contains(status)) {
                if (redirects >= MAX_REDIRECTS) {
                    throw upstreamError(HttpStatus.BAD_GATEWAY, "来源网页重定向次数过多", null);
                }
                String location = response.headers().firstValue("location")
                    .orElseThrow(() -> upstreamError(HttpStatus.BAD_GATEWAY,
                        "来源网页返回了无效重定向", null));
                return fetchBlocking(safeTarget.resolve(location), redirects + 1);
            }
            if (status < 200 || status >= 300) {
                throw upstreamError(HttpStatus.BAD_GATEWAY,
                    "来源网页返回 HTTP " + status, null);
            }
            validateContentType(response);
            long declaredLength = response.headers().firstValueAsLong("content-length").orElse(-1);
            if (declaredLength > MAX_RESPONSE_BYTES) {
                throw upstreamError(HttpStatus.CONTENT_TOO_LARGE,
                    "来源网页内容超过 1 MB 限制", null);
            }
            byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) {
                throw upstreamError(HttpStatus.CONTENT_TOO_LARGE,
                    "来源网页内容超过 1 MB 限制", null);
            }
            Charset charset = responseCharset(response, bytes);
            return new FetchedWebPage(safeTarget.toString(), new String(bytes, charset));
        }
    }

    private static URI parseUri(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw badRequest("请填写网页地址");
        }
        try {
            return URI.create(sourceUrl.trim());
        } catch (IllegalArgumentException ignored) {
            throw badRequest("网页地址必须是有效的 HTTP 或 HTTPS 地址");
        }
    }

    private static URI validatePublicTarget(URI target) throws IOException {
        String scheme = target.getScheme();
        String host = target.getHost();
        if (host == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw badRequest("网页地址必须是有效的 HTTP 或 HTTPS 地址");
        }
        if (target.getUserInfo() != null) {
            throw badRequest("网页地址不能包含用户名或密码");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.equals("localhost") || normalizedHost.endsWith(".localhost")
            || normalizedHost.endsWith(".local") || normalizedHost.endsWith(".internal")) {
            throw badRequest("只能导入公网网页，不能访问本机或内网地址");
        }
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0) {
            throw upstreamError(HttpStatus.BAD_GATEWAY, "无法解析来源网页地址", null);
        }
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw badRequest("只能导入公网网页，不能访问本机或内网地址");
            }
        }
        int port = target.getPort();
        int expectedPort = "https".equalsIgnoreCase(scheme) ? 443 : 80;
        if (port != -1 && port != expectedPort) {
            throw badRequest("网页地址仅支持标准的 80 或 443 端口");
        }
        try {
            return new URI(scheme.toLowerCase(Locale.ROOT), null, host, port,
                target.getPath(), target.getQuery(), null);
        } catch (Exception error) {
            throw badRequest("网页地址格式不正确");
        }
    }

    private static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
            || address.isLinkLocalAddress() || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            return first != 0
                && first < 224
                && !(first == 100 && second >= 64 && second <= 127)
                && !(first == 192 && second == 0 && third == 0)
                && !(first == 198 && (second == 18 || second == 19));
        }
        if (address instanceof Inet6Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            return (first & 0xfe) != 0xfc;
        }
        return false;
    }

    private static void validateContentType(HttpResponse<?> response) {
        String contentType = response.headers().firstValue("content-type").orElse("")
            .toLowerCase(Locale.ROOT);
        if (!contentType.isBlank() && !contentType.startsWith("text/html")
            && !contentType.startsWith("application/xhtml+xml")
            && !contentType.startsWith("text/plain")) {
            throw upstreamError(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "来源地址不是可解析的网页", null);
        }
    }

    private static Charset responseCharset(HttpResponse<?> response, byte[] body) {
        String contentType = response.headers().firstValue("content-type").orElse("");
        String charsetName = charsetName(contentType);
        if (charsetName == null) {
            String headerSample = new String(body, 0, Math.min(body.length, 8_192),
                StandardCharsets.ISO_8859_1);
            charsetName = charsetName(headerSample);
        }
        if (charsetName != null) {
            try {
                return Charset.forName(charsetName);
            } catch (Exception ignored) {
                // Fall back to UTF-8 for invalid or unsupported declarations.
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static String charsetName(String value) {
        var matcher = CHARSET_PATTERN.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException upstreamError(HttpStatus status, String message,
                                                          Throwable cause) {
        return new ResponseStatusException(status, message, cause);
    }
}
