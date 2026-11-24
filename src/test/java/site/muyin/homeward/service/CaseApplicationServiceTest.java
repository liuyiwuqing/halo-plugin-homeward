package site.muyin.homeward.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import site.muyin.homeward.enums.CaseStatus;
import site.muyin.homeward.model.CaseApplicationRequest;
import site.muyin.homeward.scheme.CaseApplication;
import site.muyin.homeward.scheme.MissingPersonCase;
import site.muyin.homeward.setting.HomewardGeneralSetting;
import site.muyin.homeward.setting.HomewardPublicSettings;

class CaseApplicationServiceTest {
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final HomewardPublicSettings settings = mock(HomewardPublicSettings.class);
    private final HomewardGeneralSetting general = HomewardGeneralSetting.defaults();
    private final CaseApplicationService service = new CaseApplicationService(client, settings);
    @BeforeEach void setup() {
        when(settings.general()).thenReturn(Mono.just(general));
        when(client.create(any(CaseApplication.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(client.update(any(CaseApplication.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
    }
    @Test void defaultIsClosedAndExplicitConsentIsRequired() {
        rejected(service.submit(valid(), MockServerRequest.builder().build()), HttpStatus.SERVICE_UNAVAILABLE);
        general.setApplicationSubmissionEnabled(true);
        var request = valid(); request.setConsent(false);
        rejected(service.submit(request, MockServerRequest.builder().build()), HttpStatus.BAD_REQUEST);
        verify(client, never()).create(any(CaseApplication.class));
    }
    @Test void submissionCreatesOnlyPrivatePendingApplicationAndDoesNotEchoDetails() {
        general.setApplicationSubmissionEnabled(true);
        var result = service.submit(valid(), MockServerRequest.builder().build()).block(Duration.ofSeconds(1));
        assertThat(result).containsEntry("status", "PENDING").hasSize(2);
        verify(client).create(argThat((CaseApplication item) -> item.getSpec().getStatus() == CaseApplication.Status.PENDING
            && item.getSpec().getConsentAt() != null && "私密申请人".equals(item.getSpec().getApplicantName())));
        verify(client, never()).create(any(MissingPersonCase.class));
    }
    @Test void boundsSourceUrlHoneypotAndRateLimitAreEnforced() {
        general.setApplicationSubmissionEnabled(true);
        var request = valid(); request.setDescription("x".repeat(4001));
        rejected(service.submit(request, MockServerRequest.builder().build()), HttpStatus.BAD_REQUEST);
        request = valid(); request.setSourceUrl("javascript:alert(1)");
        rejected(service.submit(request, MockServerRequest.builder().build()), HttpStatus.BAD_REQUEST);
        request = valid(); request.setWebsite("bot");
        rejected(service.submit(request, MockServerRequest.builder().build()), HttpStatus.BAD_REQUEST);
        for (int i = 0; i < 3; i++) service.submit(valid(), MockServerRequest.builder().build()).block(Duration.ofSeconds(1));
        rejected(service.submit(valid(), MockServerRequest.builder().header("X-Forwarded-For", "fake").build()), HttpStatus.TOO_MANY_REQUESTS);
    }
    @Test void approvalCreatesDraftWithoutApplicantDetailsAndRetriesReuseTheDraft() {
        var item = application();
        when(client.fetch(CaseApplication.class, "homeward-application-example")).thenReturn(Mono.just(item));
        when(client.fetch(MissingPersonCase.class, "homeward-case-application-example")).thenReturn(Mono.empty());
        when(client.create(any(MissingPersonCase.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        var accepted = service.review(item.getMetadata().getName(), true, "已电话核验授权", "reviewer").block(Duration.ofSeconds(1));
        assertThat(accepted.getSpec().getStatus()).isEqualTo(CaseApplication.Status.ACCEPTED);
        assertThat(accepted.getSpec().getHandler()).isEqualTo("reviewer");
        var captor = org.mockito.ArgumentCaptor.forClass(MissingPersonCase.class);
        verify(client).create(captor.capture());
        var draft = captor.getValue();
        assertThat(draft.getSpec().getStatus()).isEqualTo(CaseStatus.DRAFT);
        assertThat(draft.getSpec().getContactName()).isNull();
        assertThat(draft.getSpec().getContactPhone()).isNull();
        item.getSpec().setStatus(CaseApplication.Status.CONVERTING);
        when(client.fetch(MissingPersonCase.class, draft.getMetadata().getName())).thenReturn(Mono.just(draft));
        assertThat(service.review(item.getMetadata().getName(), true, "retry", "reviewer").block().getSpec().getStatus()).isEqualTo(CaseApplication.Status.ACCEPTED);
        verify(client, times(1)).create(any(MissingPersonCase.class));
    }

    @Test
    void retryRecoversWhenDraftCreationSucceedsButSavingAcceptanceFails() {
        var storedApplication = new AtomicReference<>(application());
        var storedDraft = new AtomicReference<MissingPersonCase>();
        var failAcceptance = new AtomicBoolean(true);
        String applicationName = storedApplication.get().getMetadata().getName();
        when(client.fetch(CaseApplication.class, applicationName))
            .thenAnswer(call -> Mono.just(snapshot(storedApplication.get())));
        when(client.fetch(MissingPersonCase.class, "homeward-case-application-example"))
            .thenAnswer(call -> Mono.justOrEmpty(storedDraft.get()));
        when(client.create(any(MissingPersonCase.class))).thenAnswer(call -> {
            MissingPersonCase draft = call.getArgument(0);
            storedDraft.set(draft);
            return Mono.just(draft);
        });
        when(client.update(any(CaseApplication.class))).thenAnswer(call -> {
            CaseApplication item = call.getArgument(0);
            if (item.getSpec().getStatus() == CaseApplication.Status.ACCEPTED
                && failAcceptance.getAndSet(false)) {
                return Mono.error(new IllegalStateException("simulated acceptance write failure"));
            }
            storedApplication.set(snapshot(item));
            return Mono.just(snapshot(item));
        });

        assertThatThrownBy(() -> service.review(applicationName, true, "已核实", "reviewer")
            .block(Duration.ofSeconds(1)))
            .isInstanceOf(IllegalStateException.class).hasMessage("simulated acceptance write failure");
        assertThat(storedApplication.get().getSpec().getStatus())
            .isEqualTo(CaseApplication.Status.CONVERTING);
        assertThat(storedDraft.get()).isNotNull();

        var result = service.review(applicationName, true, "重试", "reviewer")
            .block(Duration.ofSeconds(1));

        assertThat(result.getSpec().getStatus()).isEqualTo(CaseApplication.Status.ACCEPTED);
        assertThat(result.getSpec().getDraftName()).isEqualTo(storedDraft.get().getMetadata().getName());
        assertThat(result.getSpec().getReviewNote()).isEqualTo("已核实");
        verify(client, times(1)).create(any(MissingPersonCase.class));
    }

    private static CaseApplication snapshot(CaseApplication source) {
        var copy = new CaseApplication();
        copy.setMetadata(new Metadata());
        org.springframework.beans.BeanUtils.copyProperties(source.getMetadata(), copy.getMetadata());
        org.springframework.beans.BeanUtils.copyProperties(source.getSpec(), copy.getSpec());
        return copy;
    }
    @Test void rejectIsFinalAndReviewNeedsAnAuthenticatedHandler() {
        var item = application();
        when(client.fetch(CaseApplication.class, item.getMetadata().getName())).thenReturn(Mono.just(item));
        rejected(service.review(item.getMetadata().getName(), true, "", ""), HttpStatus.UNAUTHORIZED);
        assertThat(service.review(item.getMetadata().getName(), false, "无法核实", "reviewer").block().getSpec().getStatus()).isEqualTo(CaseApplication.Status.REJECTED);
        rejected(service.review(item.getMetadata().getName(), true, "", "reviewer"), HttpStatus.CONFLICT);
        verify(client, never()).create(any(MissingPersonCase.class));
    }
    private static CaseApplication application() {
        var item = new CaseApplication(); item.setMetadata(new Metadata()); item.getMetadata().setName("homeward-application-example");
        item.getSpec().setPersonName("测试寻亲人员"); item.getSpec().setApplicantName("私密申请人"); item.getSpec().setContact("私密联系");
        return item;
    }
    private static CaseApplicationRequest valid() {
        var body = new CaseApplicationRequest(); body.setPersonName("测试寻亲人员"); body.setMissingLocation("测试地点");
        body.setDescription("仅用于自动化验收的完整情况说明，这不是一份真实的寻亲申请资料。");
        body.setApplicantName("私密申请人"); body.setRelationship("亲属"); body.setContact("fixture-contact"); body.setConsent(true);
        return body;
    }
    private static void rejected(Mono<?> operation, HttpStatus status) {
        assertThatThrownBy(() -> operation.block(Duration.ofSeconds(1))).isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(status));
    }
}
