package site.muyin.homeward.support;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HomewardPublicAssetsTest {

    @Test
    void publicPageProvidesAccessibleNavigationAndLoadingState() throws Exception {
        try (var input = getClass().getResourceAsStream("/templates/homeward.html")) {
            assertThat(input).isNotNull();
            String template = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(template)
                .contains("href=\"#homeward-page-content\"")
                .contains("<h1")
                .contains("role=\"status\"")
                .contains("aria-live=\"polite\"")
                .contains("th:if=\"${homeward404}\"")
                .contains("404 · 页面未找到")
                .contains("window.__HOMEWARD_404__")
                .contains("homeward-common.js")
                .contains("homeward-clue.js");
        }
    }

    @Test
    void publicPageLoadsSettingsAndPagedCaseList() throws Exception {
        try (var input = getClass().getResourceAsStream(
            "/static/homeward/homeward-public.js")) {
            assertThat(input).isNotNull();
            String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(script)
                .contains("/widget?surface=page")
                .contains("/cases?${params}")
                .contains("popstate");
        }
    }

    @Test
    void clueSubmissionUsesAnAccessibleDismissibleDialog() throws Exception {
        try (var input = getClass().getResourceAsStream(
            "/static/homeward/homeward-clue.js")) {
            assertThat(input).isNotNull();
            String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(script)
                .contains("role=\"dialog\"")
                .contains("aria-modal=\"true\"")
                .contains("data-homeward-dialog-close")
                .contains("event.key === 'Escape'");
        }
    }
}
