package site.muyin.homeward.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.notification.NotificationCenter;
import run.halo.app.notification.NotificationReasonEmitter;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.homeward.enums.ClueStatus;
import site.muyin.homeward.model.ClueStatusUpdateRequest;
import site.muyin.homeward.notify.ClueSubmittedNotifyService;
import site.muyin.homeward.scheme.ClueReport;
import site.muyin.homeward.service.impl.ClueReportServiceImpl;
import site.muyin.homeward.setting.HomewardPublicSettings;

class ClueProcessingHistoryTest {
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final ReactiveSettingFetcher settings = mock(ReactiveSettingFetcher.class);
    private final ClueReportServiceImpl service = new ClueReportServiceImpl(client,
        new HomewardPublicSettings(settings), new ClueProtectionService(),
        new ClueSubmittedNotifyService(mock(NotificationReasonEmitter.class),
            mock(NotificationCenter.class), settings));
    private final ClueReport clue = new ClueReport();

    @BeforeEach
    void configureStorage() {
        clue.setMetadata(new Metadata());
        clue.getMetadata().setName("clue-for-history");
        when(client.fetch(ClueReport.class, "clue-for-history")).thenReturn(Mono.just(clue));
        when(client.update(any(ClueReport.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    @Test
    void multipleHandlersAppendRecordsWithoutOverwritingPriorNotes() {
        service.updateStatus("clue-for-history", request(ClueStatus.CONTACTED, "  已联系核实  "),
            "staff-li").block(Duration.ofSeconds(1));
        var result = service.updateStatus("clue-for-history",
            request(ClueStatus.RESOLVED, "确认线索处理完成"), "staff-wang").block(Duration.ofSeconds(1));

        assertThat(result.getHandler()).isEqualTo("staff-wang");
        assertThat(result.getStatus()).isEqualTo(ClueStatus.RESOLVED);
        assertThat(result.getProcessingHistory()).hasSize(2);
        assertThat(result.getProcessingHistory().get(0)).satisfies(record -> {
            assertThat(record.getPreviousStatus()).isEqualTo(ClueStatus.PENDING);
            assertThat(record.getStatus()).isEqualTo(ClueStatus.CONTACTED);
            assertThat(record.getHandler()).isEqualTo("staff-li");
            assertThat(record.getProcessNote()).isEqualTo("已联系核实");
            assertThat(record.getProcessedAt()).isNotNull();
            assertThat(record.isLegacy()).isFalse();
        });
        assertThat(result.getProcessingHistory().get(1)).satisfies(record -> {
            assertThat(record.getPreviousStatus()).isEqualTo(ClueStatus.CONTACTED);
            assertThat(record.getHandler()).isEqualTo("staff-wang");
            assertThat(record.getProcessNote()).isEqualTo("确认线索处理完成");
            assertThat(record.getProcessedAt()).isEqualTo(result.getUpdatedAt());
        });
    }

    @Test
    void oldLastNoteIsPreservedAsOneLegacyRecordBeforeFirstNewRecord() {
        Instant oldTime = Instant.parse("2026-10-10T03:00:00Z");
        clue.getSpec().setProcessingHistory(null);
        clue.getSpec().setStatus(ClueStatus.CONTACTED);
        clue.getSpec().setHandler("old-handler");
        clue.getSpec().setProcessNote("旧版最后一次处理意见");
        clue.getSpec().setUpdatedAt(oldTime);

        service.updateStatus("clue-for-history", request(ClueStatus.RESOLVED, "完成核实"),
            "current-staff").block(Duration.ofSeconds(1));
        var result = service.updateStatus("clue-for-history", request(ClueStatus.RESOLVED, "补记说明"),
            "current-staff").block(Duration.ofSeconds(1));

        assertThat(result.getProcessingHistory()).hasSize(3);
        assertThat(result.getProcessingHistory().get(0)).satisfies(record -> {
            assertThat(record.isLegacy()).isTrue();
            assertThat(record.getPreviousStatus()).isNull();
            assertThat(record.getHandler()).isEqualTo("old-handler");
            assertThat(record.getProcessNote()).isEqualTo("旧版最后一次处理意见");
            assertThat(record.getProcessedAt()).isEqualTo(oldTime);
        });
        assertThat(result.getProcessingHistory().stream().filter(ClueReport.ProcessingRecord::isLegacy))
            .hasSize(1);
    }

    @Test
    void untouchedOldClueWithNullHistoryStartsWithoutInventedHistory() {
        clue.getSpec().setProcessingHistory(null);
        var result = service.updateStatus("clue-for-history", request(ClueStatus.CONTACTED, " "),
            "staff-li").block(Duration.ofSeconds(1));
        assertThat(result.getProcessingHistory()).singleElement().satisfies(record -> {
            assertThat(record.isLegacy()).isFalse();
            assertThat(record.getProcessNote()).isNull();
        });
    }

    @Test
    void rejectsUnauthenticatedHandlerAndOversizedNotesWithoutChangingClue() {
        assertThatThrownBy(() -> service.updateStatus("clue-for-history",
            request(ClueStatus.CONTACTED, "备注"), " ").block(Duration.ofSeconds(1)))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> service.updateStatus("clue-for-history",
            request(ClueStatus.CONTACTED, "字".repeat(2001)), "staff-li").block(Duration.ofSeconds(1)))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(clue.getSpec().getStatus()).isEqualTo(ClueStatus.PENDING);
        assertThat(clue.getSpec().getProcessingHistory()).isEmpty();
        var result = service.updateStatus("clue-for-history",
            request(ClueStatus.CONTACTED, "字".repeat(2000)), "staff-li").block(Duration.ofSeconds(1));
        assertThat(result.getProcessNote()).hasSize(2000);
    }

    private static ClueStatusUpdateRequest request(ClueStatus status, String note) {
        var request = new ClueStatusUpdateRequest();
        request.setStatus(status);
        request.setProcessNote(note);
        return request;
    }
}
