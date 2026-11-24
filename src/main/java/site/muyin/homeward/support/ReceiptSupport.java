package site.muyin.homeward.support;

import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ResponseStatusException;

/** 回执是持有即授权的随机凭证，明文不写入模型、URL 或日志。 */
@Component
public class ReceiptSupport {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private final Clock clock;
    private final Map<String, Deque<Instant>> attempts = new HashMap<>();
    private final Deque<Instant> globalAttempts = new ArrayDeque<>();

    public ReceiptSupport() {
        this(Clock.systemUTC());
    }

    ReceiptSupport(Clock clock) {
        this.clock = clock;
    }

    public static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hashToken(String token) {
        return HomewardRequestSupport.sha256(token, "homeward-clue-receipt-v1");
    }

    public static boolean validToken(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    public void checkLookupRate(ServerRequest request) {
        // 不信任任意请求附带的转发头；反向代理部署可共享此限额，另有全局限额兜底。
        String address = request.remoteAddress().map(InetSocketAddress::getHostString)
            .orElse("unknown");
        checkLookupRate(HomewardRequestSupport.sha256(address, "receipt-rate"));
    }

    synchronized void checkLookupRate(String addressHash) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(WINDOW);
        while (!globalAttempts.isEmpty() && !globalAttempts.peekFirst().isAfter(cutoff)) {
            globalAttempts.removeFirst();
        }
        attempts.values().forEach(bucket -> {
            while (!bucket.isEmpty() && !bucket.peekFirst().isAfter(cutoff)) bucket.removeFirst();
        });
        attempts.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        Deque<Instant> bucket = attempts.get(addressHash);
        if (globalAttempts.size() >= 300 || (bucket != null && bucket.size() >= 30)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "查询太频繁，请稍后再试");
        }
        globalAttempts.addLast(now);
        attempts.computeIfAbsent(addressHash, ignored -> new ArrayDeque<>()).addLast(now);
    }
}
