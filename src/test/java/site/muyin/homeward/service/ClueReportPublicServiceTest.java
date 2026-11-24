package site.muyin.homeward.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.notification.NotificationCenter;
import run.halo.app.notification.NotificationReasonEmitter;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.homeward.model.ClueSubmitRequest;
import site.muyin.homeward.notify.ClueSubmittedNotifyService;
import site.muyin.homeward.scheme.ClueReport;
import site.muyin.homeward.scheme.MissingPersonCase;
import site.muyin.homeward.service.impl.ClueReportServiceImpl;
import site.muyin.homeward.setting.HomewardGeneralSetting;
import site.muyin.homeward.setting.HomewardNotificationSetting;
import site.muyin.homeward.setting.HomewardProtectionSetting;
import site.muyin.homeward.setting.HomewardPublicSettings;
import site.muyin.homeward.support.ReceiptSupport;

class ClueReportPublicServiceTest {

    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final ReactiveSettingFetcher settings = mock(ReactiveSettingFetcher.class);
    private final HomewardGeneralSetting general = HomewardGeneralSetting.defaults();
    private final HomewardProtectionSetting protection = HomewardProtectionSetting.defaults();
    private final ClueReportServiceImpl service = new ClueReportServiceImpl(client,
        new HomewardPublicSettings(settings), new ClueProtectionService(),
        new ClueSubmittedNotifyService(mock(NotificationReasonEmitter.class),
            mock(NotificationCenter.class), settings));

    @BeforeEach
    void configure() {
        when(settings.fetch(HomewardGeneralSetting.GROUP, HomewardGeneralSetting.class))
            .thenReturn(Mono.just(general));
        when(settings.fetch(HomewardProtectionSetting.GROUP, HomewardProtectionSetting.class))
            .thenReturn(Mono.just(protection));
        var notification = HomewardNotificationSetting.defaults();
        notification.setEnabled(false);
        when(settings.fetch(HomewardNotificationSetting.GROUP, HomewardNotificationSetting.class))
            .thenReturn(Mono.just(notification));
        var item = new MissingPersonCase();
        item.setMetadata(new Metadata());
        item.getMetadata().setName("homeward-case-person-a");
        item.getSpec().setSlug("person-a");
        item.getSpec().setTitle("小安的寻亲信息");
        when(client.listBy(eq(MissingPersonCase.class), any(), any()))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1, List.of(item))));
        when(client.create(any(ClueReport.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    @Test
    void disabledPluginRejectsCluesWithServiceUnavailable() {
        general.setEnabled(false);
        assertRejected(validClue(), HttpStatus.SERVICE_UNAVAILABLE, "归航寻亲暂未开放");
    }

    @Test
    void closedClueSubmissionIsDifferentFromRateLimiting() {
        protection.setClueSubmissionEnabled(false);
        assertRejected(validClue(), HttpStatus.SERVICE_UNAVAILABLE, "线索提交暂未开放");
    }

    @Test
    void missingContactAndShortContentAreBadRequests() {
        var request = validClue();
        request.setContact("  ");
        assertRejected(request, HttpStatus.BAD_REQUEST, "请留下联系方式");
        request = validClue();
        request.setContent("太短");
        assertRejected(request, HttpStatus.BAD_REQUEST, "线索内容太短");
    }

    @Test
    void oversizedFieldsAreRejectedBeforePersistence() {
        var request = validClue();
        request.setContact("x".repeat(121));
        assertRejected(request, HttpStatus.BAD_REQUEST, "长度限制");
    }

    @Test
    void onlyActualRateLimitingReturnsTooManyRequests() {
        protection.setMaxSubmissionsPerHour(1);
        var result = service.submit("person-a", validClue(), serverRequest())
            .block(Duration.ofSeconds(1));
        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(ReceiptSupport.validToken(result.getReceiptToken())).isTrue();
        var stored = ArgumentCaptor.forClass(ClueReport.class);
        verify(client).create(stored.capture());
        assertThat(stored.getValue().getSpec().getReceiptTokenHash())
            .isEqualTo(ReceiptSupport.hashToken(result.getReceiptToken()))
            .isNotEqualTo(result.getReceiptToken());
        assertThat(stored.getValue().toString()).doesNotContain(result.getReceiptToken());
        assertRejected(validClue(), HttpStatus.TOO_MANY_REQUESTS, "提交太频繁");
    }

    @Test
    void settingsFailureDoesNotAcceptAClue() {
        when(settings.fetch(HomewardGeneralSetting.GROUP, HomewardGeneralSetting.class))
            .thenReturn(Mono.error(new IllegalStateException("settings unavailable")));
        assertThatThrownBy(() -> service.submit("person-a", validClue(), serverRequest())
            .block(Duration.ofSeconds(1))).isInstanceOf(IllegalStateException.class)
            .hasMessage("settings unavailable");
    }

    private void assertRejected(ClueSubmitRequest request, HttpStatus status, String reason) {
        assertThatThrownBy(() -> service.submit("person-a", request, serverRequest())
            .block(Duration.ofSeconds(1)))
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                assertThat(error.getStatusCode()).isEqualTo(status);
                assertThat(error.getReason()).contains(reason);
            });
    }

    private static MockServerRequest serverRequest() {
        return MockServerRequest.builder().header("X-Real-IP", "192.0.2.2").build();
    }

    private static ClueSubmitRequest validClue() {
        var request = new ClueSubmitRequest();
        request.setContact("13800000000");
        request.setContent("我在南京车站附近看到过一个相似的人");
        return request;
    }
}
