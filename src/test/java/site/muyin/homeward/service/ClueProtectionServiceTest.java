package site.muyin.homeward.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import site.muyin.homeward.model.ClueSubmitRequest;
import site.muyin.homeward.setting.HomewardProtectionSetting;

class ClueProtectionServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-07-20T10:00:00Z"),
        ZoneOffset.UTC);

    @Test
    void rejectsHoneypotSubmission() {
        var service = new ClueProtectionService(fixedClock);
        var request = new ClueSubmitRequest();
        request.setWebsite("bot-site");

        var result = service.tryAccept("case-a", "ip-hash", request,
            HomewardProtectionSetting.defaults());

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getReason()).contains("提交异常");
    }

    @Test
    void rejectsWhenIpCasePairExceedsHourlyLimit() {
        var service = new ClueProtectionService(fixedClock);
        var setting = HomewardProtectionSetting.defaults();
        setting.setMaxSubmissionsPerHour(1);
        var request = new ClueSubmitRequest();
        request.setContact("13800000000");
        request.setContent("我在车站附近看到过一个相似的人");

        assertThat(service.tryAccept("case-a", "ip-hash", request, setting).isAllowed()).isTrue();
        var blocked = service.tryAccept("case-a", "ip-hash", request, setting);

        assertThat(blocked.isAllowed()).isFalse();
        assertThat(blocked.getReason()).contains("提交太频繁");
    }
}
