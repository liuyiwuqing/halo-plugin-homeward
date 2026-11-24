package site.muyin.homeward.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.homeward.enums.ClueStatus;
import site.muyin.homeward.model.ClueReceiptVO;
import site.muyin.homeward.scheme.ClueReport;
import site.muyin.homeward.setting.HomewardGeneralSetting;
import site.muyin.homeward.setting.HomewardPublicSettings;
import site.muyin.homeward.support.ReceiptSupport;

class ClueReceiptServiceTest {
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final ReactiveSettingFetcher settings = mock(ReactiveSettingFetcher.class);
    private final ClueReceiptService service =
        new ClueReceiptService(client, new HomewardPublicSettings(settings));

    @BeforeEach
    void enablePublicAccess() {
        when(settings.fetch(HomewardGeneralSetting.GROUP, HomewardGeneralSetting.class))
            .thenReturn(Mono.just(HomewardGeneralSetting.defaults()));
    }

    @Test
    void receiptUsesTokenDigestAndReturnsOnlyPublicStatusAndTimestamps() {
        String token = ReceiptSupport.generateToken();
        var clue = privateClue();
        clue.getSpec().setStatus(ClueStatus.CONTACTED);
        when(client.listBy(eq(ClueReport.class), any(), any()))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1, List.of(clue))));

        var result = service.lookup(token).block(Duration.ofSeconds(1));

        assertThat(result.status()).isEqualTo(ClueStatus.CONTACTED);
        assertThat(result.submittedAt()).isEqualTo(Instant.parse("2026-10-10T00:00:00Z"));
        assertThat(result.updatedAt()).isEqualTo(Instant.parse("2026-10-11T00:00:00Z"));
        assertThat(result.message()).contains("联系核实").doesNotContain("private");
        assertThat(Arrays.stream(ClueReceiptVO.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName))
            .containsExactly("status", "submittedAt", "updatedAt", "message");
        var options = ArgumentCaptor.forClass(ListOptions.class);
        verify(client).listBy(eq(ClueReport.class), options.capture(), any());
        assertThat(options.getValue().toString())
            .contains("spec.receiptTokenHash = " + ReceiptSupport.hashToken(token),
                "metadata.deletionTimestamp IS NULL")
            .doesNotContain(token);
    }

    @Test
    void invalidMissingAndDeletedReceiptsShareTheSameNotFoundResponse() {
        when(client.listBy(eq(ClueReport.class), any(), any()))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 0, List.of())));
        for (String token : new String[]{"invalid", ReceiptSupport.generateToken()}) {
            assertThatThrownBy(() -> service.lookup(token).block(Duration.ofSeconds(1)))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(error.getReason()).isEqualTo("回执不存在，请核对凭证");
                });
        }
    }

    @Test
    void oldClueWithoutStatusUsesPendingAndDoesNotRequireMissingHistoryFields() {
        var clue = privateClue();
        clue.getSpec().setStatus(null);
        clue.getSpec().setProcessingHistory(null);
        when(client.listBy(eq(ClueReport.class), any(), any()))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1, List.of(clue))));

        var result = service.lookup(ReceiptSupport.generateToken()).block(Duration.ofSeconds(1));

        assertThat(result.status()).isEqualTo(ClueStatus.PENDING);
        assertThat(result.message()).contains("等待工作人员核实");
    }

    @Test
    void disabledPluginRejectsEvenValidReceiptTokens() {
        var disabled = HomewardGeneralSetting.defaults();
        disabled.setEnabled(false);
        when(settings.fetch(HomewardGeneralSetting.GROUP, HomewardGeneralSetting.class))
            .thenReturn(Mono.just(disabled));
        assertThatThrownBy(() -> service.lookup(ReceiptSupport.generateToken())
            .block(Duration.ofSeconds(1)))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private static ClueReport privateClue() {
        var clue = new ClueReport();
        var spec = clue.getSpec();
        spec.setContact("private-contact");
        spec.setContent("private-content");
        spec.setCaseTitle("private-case-title");
        spec.setHandler("private-handler");
        spec.setProcessNote("private-internal-note");
        spec.setIpHash("private-ip-hash");
        spec.setSubmittedAt(Instant.parse("2026-10-10T00:00:00Z"));
        spec.setUpdatedAt(Instant.parse("2026-10-11T00:00:00Z"));
        return clue;
    }
}
