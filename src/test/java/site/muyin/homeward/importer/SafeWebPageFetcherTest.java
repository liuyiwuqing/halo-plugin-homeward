package site.muyin.homeward.importer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class SafeWebPageFetcherTest {

    private final SafeWebPageFetcher fetcher = new SafeWebPageFetcher();

    @Test
    void refusesPrivateAndLoopbackTargetsBeforeConnecting() {
        assertThatThrownBy(() -> fetcher.fetch("http://127.0.0.1:8090/internal").block())
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                org.assertj.core.api.Assertions.assertThat(error.getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
                org.assertj.core.api.Assertions.assertThat(error.getReason()).contains("公网");
            });

        assertThatThrownBy(() -> fetcher.fetch("http://169.254.169.254/latest/meta-data").block())
            .isInstanceOf(ResponseStatusException.class);
    }
}
