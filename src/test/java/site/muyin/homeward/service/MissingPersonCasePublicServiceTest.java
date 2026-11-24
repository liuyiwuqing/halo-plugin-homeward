package site.muyin.homeward.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.homeward.scheme.MissingPersonCase;
import site.muyin.homeward.service.impl.MissingPersonCaseServiceImpl;
import site.muyin.homeward.setting.HomewardGeneralSetting;
import site.muyin.homeward.setting.HomewardPublicSettings;
import site.muyin.homeward.setting.HomewardDisplaySetting;
import site.muyin.homeward.setting.HomewardProtectionSetting;
import site.muyin.homeward.query.MissingPersonCasePublicQuery;

class MissingPersonCasePublicServiceTest {

    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final ReactiveSettingFetcher settings = mock(ReactiveSettingFetcher.class);
    private final MissingPersonCaseServiceImpl service =
        new MissingPersonCaseServiceImpl(client, new HomewardPublicSettings(settings));

    @Test
    void disabledPluginRejectsKnownPublicDetail() {
        var general = HomewardGeneralSetting.defaults();
        general.setEnabled(false);
        when(settings.fetch(HomewardGeneralSetting.GROUP, HomewardGeneralSetting.class))
            .thenReturn(Mono.just(general));
        var item = new MissingPersonCase();
        item.getSpec().setSlug("published-person");
        when(client.listBy(eq(MissingPersonCase.class), any(), any()))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1, List.of(item))));

        assertThatThrownBy(() -> service.getPublishedBySlug("published-person")
            .block(Duration.ofSeconds(1)))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> org.assertj.core.api.Assertions.assertThat(error.getStatusCode())
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void disabledPluginRejectsPublicListButReturnsDisabledWidgetConfiguration() {
        var general = HomewardGeneralSetting.defaults();
        general.setEnabled(false);
        configure(general, HomewardDisplaySetting.defaults(), HomewardProtectionSetting.defaults());

        assertThatThrownBy(() -> service.listForPublic(new MissingPersonCasePublicQuery(null, 1, 20))
            .block(Duration.ofSeconds(1)))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        var widget = service.widget(true).block(Duration.ofSeconds(1));
        assertThat(widget.isEnabled()).isFalse();
        assertThat(widget.isClueSubmissionEnabled()).isFalse();
        assertThat(widget.getCases()).isEmpty();
    }

    @Test
    void widgetExposesSubmissionPolicyAndSurfacePageSize() {
        var display = HomewardDisplaySetting.defaults();
        display.setPublicPageCaseLimit(35);
        display.setWidgetCaseLimit(6);
        var protection = HomewardProtectionSetting.defaults();
        protection.setClueSubmissionEnabled(false);
        protection.setMinContentLength(30);
        configure(HomewardGeneralSetting.defaults(), display, protection);
        when(client.listBy(eq(MissingPersonCase.class), any(), any()))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 0, List.of())));

        var pageWidget = service.widget(true).block(Duration.ofSeconds(1));
        var errorWidget = service.widget(false).block(Duration.ofSeconds(1));

        assertThat(pageWidget.isEnabled()).isTrue();
        assertThat(pageWidget.isClueSubmissionEnabled()).isFalse();
        assertThat(pageWidget.getMinContentLength()).isEqualTo(30);
        assertThat(pageWidget.getPageSize()).isEqualTo(35);
        assertThat(errorWidget.getPageSize()).isEqualTo(6);
    }

    @Test
    void listPreservesPaginationAndExposesPublicUpdatedTime() {
        when(settings.fetch(HomewardGeneralSetting.GROUP, HomewardGeneralSetting.class))
            .thenReturn(Mono.empty());
        var item = new MissingPersonCase();
        item.getSpec().setSlug("person-a");
        item.getSpec().setUpdatedAt(Instant.parse("2026-10-11T01:02:03Z"));
        when(client.listBy(eq(MissingPersonCase.class), any(), any()))
            .thenReturn(Mono.just(new ListResult<>(2, 10, 11, List.of(item))));

        var result = service.listForPublic(new MissingPersonCasePublicQuery("南京", 2, 10))
            .block(Duration.ofSeconds(1));

        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getTotal()).isEqualTo(11);
        assertThat(result.getItems()).singleElement().satisfies(publicCase -> {
            assertThat(publicCase.getSlug()).isEqualTo("person-a");
            assertThat(publicCase.getUpdatedAt()).isEqualTo(Instant.parse("2026-10-11T01:02:03Z"));
        });
    }

    @Test
    void failedSettingsReadDoesNotOpenPublicData() {
        when(settings.fetch(HomewardGeneralSetting.GROUP, HomewardGeneralSetting.class))
            .thenReturn(Mono.error(new IllegalStateException("settings unavailable")));
        assertThatThrownBy(() -> service.getPublishedBySlug("person-a").block(Duration.ofSeconds(1)))
            .isInstanceOf(IllegalStateException.class).hasMessage("settings unavailable");
        assertThatThrownBy(() -> service.listForPublic(new MissingPersonCasePublicQuery(null, 1, 20))
            .block(Duration.ofSeconds(1)))
            .isInstanceOf(IllegalStateException.class).hasMessage("settings unavailable");
    }

    private void configure(HomewardGeneralSetting general, HomewardDisplaySetting display,
                           HomewardProtectionSetting protection) {
        when(settings.fetch(HomewardGeneralSetting.GROUP, HomewardGeneralSetting.class))
            .thenReturn(Mono.just(general));
        when(settings.fetch(HomewardDisplaySetting.GROUP, HomewardDisplaySetting.class))
            .thenReturn(Mono.just(display));
        when(settings.fetch(HomewardProtectionSetting.GROUP, HomewardProtectionSetting.class))
            .thenReturn(Mono.just(protection));
    }

    @Test
    void closedSharedCaseHasOnlyGoneStatusAndUnpublishedFoundCaseIsStillHidden() {
        when(settings.fetch(HomewardGeneralSetting.GROUP, HomewardGeneralSetting.class)).thenReturn(Mono.just(HomewardGeneralSetting.defaults()));
        var found = new MissingPersonCase();
        found.getSpec().setPublishedAt(Instant.now());
        when(client.listBy(eq(MissingPersonCase.class), any(), any())).thenReturn(
            Mono.just(new ListResult<>(1,1,0,List.of())), Mono.just(new ListResult<>(1,1,1,List.of(found))));
        assertThatThrownBy(() -> service.getPublishedBySlug("closed").block()).isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.GONE));
        found.getSpec().setPublishedAt(null);
        when(client.listBy(eq(MissingPersonCase.class), any(), any())).thenReturn(
            Mono.just(new ListResult<>(1,1,0,List.of())), Mono.just(new ListResult<>(1,1,1,List.of(found))));
        assertThatThrownBy(() -> service.getPublishedBySlug("unpublished").block()).isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void oncePublishedSlugCannotBeChangedEvenAfterWithdrawal() {
        var old = new MissingPersonCase(); old.setMetadata(new run.halo.app.extension.Metadata()); old.getMetadata().setName("example");
        old.getSpec().setSlug("stable"); old.getSpec().setPublishedAt(Instant.now());
        var edit = new MissingPersonCase(); edit.setMetadata(old.getMetadata()); edit.getSpec().setPersonName("Test"); edit.getSpec().setSlug("new-slug");
        when(client.fetch(MissingPersonCase.class,"example")).thenReturn(Mono.just(old));
        assertThatThrownBy(() -> service.update(edit).block()).isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void publishedAndFoundCasesKeepTheirSlugEvenWhenLegacyTimestampsAreMissing() {
        for (var status : List.of(site.muyin.homeward.enums.CaseStatus.PUBLISHED,
            site.muyin.homeward.enums.CaseStatus.FOUND)) {
            var old = new MissingPersonCase();
            old.setMetadata(new run.halo.app.extension.Metadata());
            old.getMetadata().setName("legacy-example");
            old.getSpec().setSlug("stable");
            old.getSpec().setStatus(status);
            var edit = new MissingPersonCase();
            edit.setMetadata(old.getMetadata());
            edit.getSpec().setPersonName("Test");
            edit.getSpec().setSlug("new-slug");
            when(client.fetch(MissingPersonCase.class, "legacy-example")).thenReturn(Mono.just(old));

            assertThatThrownBy(() -> service.update(edit).block())
                .isInstanceOfSatisfying(ResponseStatusException.class,
                    error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        }
    }

    @Test
    void creatingAFoundCaseRecordsTheClosureTime() {
        var item = new MissingPersonCase();
        item.getSpec().setPersonName("Test");
        item.getSpec().setSlug("found-example");
        item.getSpec().setStatus(site.muyin.homeward.enums.CaseStatus.FOUND);
        when(client.create(any(MissingPersonCase.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        var result = service.create(item).block(Duration.ofSeconds(1));

        assertThat(result.getFoundAt()).isNotNull();
        assertThat(result.getPublishedAt()).isNull();
    }
}
