package site.muyin.homeward.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

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
                .contains("aria-live=\"polite\"");
        }
    }

    @Test
    void publicPageRequestsWidgetWithPageSurface() throws Exception {
        try (var input = getClass().getResourceAsStream(
            "/static/homeward/homeward-public.js")) {
            assertThat(input).isNotNull();
            String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(script).contains("/widget?surface=page");
        }
    }

    @Test
    void clueSubmissionUsesAnAccessibleDismissibleDialog() throws Exception {
        try (var input = getClass().getResourceAsStream(
            "/static/homeward/homeward-public.js")) {
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
