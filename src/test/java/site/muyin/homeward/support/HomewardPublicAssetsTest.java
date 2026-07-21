package site.muyin.homeward.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HomewardPublicAssetsTest {

    @Test
    void publicPageRequestsWidgetWithPageSurface() throws Exception {
        try (var input = getClass().getResourceAsStream(
            "/static/homeward/homeward-public.js")) {
            assertThat(input).isNotNull();
            String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(script).contains("/widget?surface=page");
        }
    }
}
