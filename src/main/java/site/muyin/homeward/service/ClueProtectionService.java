package site.muyin.homeward.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;
import site.muyin.homeward.model.ClueSubmitRequest;
import site.muyin.homeward.model.ProtectionCheckResult;
import site.muyin.homeward.setting.HomewardProtectionSetting;

@Service
public class ClueProtectionService {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final Clock clock;

    private final ConcurrentMap<String, Deque<Instant>> submissions = new ConcurrentHashMap<>();

    public ClueProtectionService() {
        this(Clock.systemUTC());
    }

    public ClueProtectionService(Clock clock) {
        this.clock = clock;
    }

    public ProtectionCheckResult tryAccept(String caseSlug, String ipHash, ClueSubmitRequest request,
                                           HomewardProtectionSetting setting) {
        var effectiveSetting = setting == null ? HomewardProtectionSetting.defaults() : setting;
        if (!effectiveSetting.clueSubmissionEnabledOrDefault()) {
            return ProtectionCheckResult.rejected("线索提交暂未开放");
        }
        if (request == null) {
            return ProtectionCheckResult.rejected("请求内容不能为空");
        }
        if (hasText(request.getWebsite())) {
            return ProtectionCheckResult.rejected("提交异常，请刷新页面后重试");
        }
        if (!hasText(request.getContact())) {
            return ProtectionCheckResult.rejected("请留下联系方式");
        }
        if (!hasText(request.getContent())
            || request.getContent().trim().length() < effectiveSetting.minContentLengthOrDefault()) {
            return ProtectionCheckResult.rejected("线索内容太短，请补充更多细节");
        }

        int limit = effectiveSetting.maxSubmissionsPerHourOrDefault();
        if (limit == 0) {
            return ProtectionCheckResult.allowed();
        }

        String key = caseSlug + ":" + ipHash;
        Instant now = clock.instant();
        Deque<Instant> bucket = submissions.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && bucket.peekFirst().plus(WINDOW).isBefore(now)) {
                bucket.removeFirst();
            }
            if (bucket.size() >= limit) {
                return ProtectionCheckResult.rejected("提交太频繁，请稍后再试");
            }
            bucket.addLast(now);
        }
        return ProtectionCheckResult.allowed();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
