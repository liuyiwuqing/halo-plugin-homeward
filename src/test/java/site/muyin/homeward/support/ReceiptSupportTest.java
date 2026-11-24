package site.muyin.homeward.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.web.server.ResponseStatusException;

class ReceiptSupportTest {

    @Test
    void tokensAreIndependent256BitUrlSafeCredentialsAndOnlyTheirDigestIsStable() {
        var tokens = new HashSet<String>();
        for (int i = 0; i < 100; i++) {
            String token = ReceiptSupport.generateToken();
            assertThat(ReceiptSupport.validToken(token)).isTrue();
            assertThat(java.util.Base64.getUrlDecoder().decode(token)).hasSize(32);
            assertThat(tokens.add(token)).isTrue();
            String digest = ReceiptSupport.hashToken(token);
            assertThat(digest).matches("[0-9a-f]{64}").isNotEqualTo(token);
            assertThat(ReceiptSupport.hashToken(token)).isEqualTo(digest);
        }
        assertThat(ReceiptSupport.validToken(null)).isFalse();
        assertThat(ReceiptSupport.validToken("x".repeat(42))).isFalse();
        assertThat(ReceiptSupport.validToken("!".repeat(43))).isFalse();
    }

    @Test
    void thirtyRequestsPerAddressAreAllowedAndWindowReopensAtExactlyOneMinute() {
        var clock = mock(Clock.class);
        Instant start = Instant.parse("2026-10-11T00:00:00Z");
        when(clock.instant()).thenReturn(start);
        var support = new ReceiptSupport(clock);
        for (int i = 0; i < 30; i++) support.checkLookupRate("address-a");
        assertRateLimited(() -> support.checkLookupRate("address-a"));
        assertThatCode(() -> support.checkLookupRate("address-b")).doesNotThrowAnyException();
        when(clock.instant()).thenReturn(start.plusSeconds(60));
        assertThatCode(() -> support.checkLookupRate("address-a")).doesNotThrowAnyException();
    }

    @Test
    void globalLimitBoundsRequestsAcrossDifferentAddresses() {
        var clock = mock(Clock.class);
        Instant start = Instant.parse("2026-10-11T00:00:00Z");
        when(clock.instant()).thenReturn(start);
        var support = new ReceiptSupport(clock);
        for (int i = 0; i < 300; i++) support.checkLookupRate("address-" + i);
        assertRateLimited(() -> support.checkLookupRate("another-address"));
        when(clock.instant()).thenReturn(start.plusSeconds(60));
        assertThatCode(() -> support.checkLookupRate("another-address")).doesNotThrowAnyException();
    }

    @Test
    void arbitraryForwardingHeadersDoNotBypassThePeerAddressLimit() {
        var support = new ReceiptSupport();
        for (int i = 0; i < 30; i++) {
            support.checkLookupRate(MockServerRequest.builder()
                .remoteAddress(new InetSocketAddress("192.0.2.2", 1234))
                .header("X-Forwarded-For", "198.51.100." + i).build());
        }
        assertRateLimited(() -> support.checkLookupRate(MockServerRequest.builder()
            .remoteAddress(new InetSocketAddress("192.0.2.2", 2345))
            .header("X-Forwarded-For", "203.0.113.9").build()));
    }

    private static void assertRateLimited(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }
}
