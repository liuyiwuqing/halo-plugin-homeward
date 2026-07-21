package site.muyin.homeward.notify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.notification.NotificationCenter;
import run.halo.app.notification.NotificationReasonEmitter;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.homeward.enums.ClueStatus;
import site.muyin.homeward.scheme.ClueReport;
import site.muyin.homeward.setting.HomewardNotificationSetting;

@ExtendWith(MockitoExtension.class)
class ClueSubmittedNotifyServiceTest {

    @Mock
    NotificationReasonEmitter notificationReasonEmitter;

    @Mock
    NotificationCenter notificationCenter;

    @Mock
    ReactiveSettingFetcher settingFetcher;

    @InjectMocks
    ClueSubmittedNotifyService service;

    @Test
    void notifiesDefaultAdminWhenClueSubmitted() {
        var clue = clueReport();
        when(settingFetcher.fetch(HomewardNotificationSetting.GROUP, HomewardNotificationSetting.class))
            .thenReturn(Mono.empty());
        when(notificationCenter.subscribe(any(), any())).thenReturn(Mono.empty());
        when(notificationReasonEmitter.emit(eq(ClueSubmittedNotifyService.REASON_TYPE), any()))
            .thenReturn(Mono.empty());

        service.notifySubmitted(clue).block(Duration.ofSeconds(1));

        verify(notificationCenter).subscribe(any(), any());
        verify(notificationReasonEmitter).emit(eq(ClueSubmittedNotifyService.REASON_TYPE), any());
    }

    private static ClueReport clueReport() {
        var clue = new ClueReport();
        clue.setMetadata(new Metadata());
        clue.getMetadata().setName("homeward-clue-test");
        clue.getSpec().setCaseTitle("小安的寻亲信息");
        clue.getSpec().setCaseSlug("case-a");
        clue.getSpec().setSubmitterName("热心访客");
        clue.getSpec().setContact("13800000000");
        clue.getSpec().setContent("我在车站附近看到过相似人员");
        clue.getSpec().setStatus(ClueStatus.PENDING);
        return clue;
    }
}
