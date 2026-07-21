package site.muyin.homeward.support;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

public final class HomewardNames {

    private HomewardNames() {
    }

    public static String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static String slugOrGenerated(String raw, String fallbackPrefix) {
        String normalized = toDnsToken(raw);
        if (normalized.isBlank()) {
            return fallbackPrefix + "-" + randomSuffix();
        }
        return normalized;
    }

    public static String toDnsToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String ascii = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9-]+", "-")
            .replaceAll("-{2,}", "-")
            .replaceAll("^-|-$", "");
        if (ascii.length() > 80) {
            ascii = ascii.substring(0, 80).replaceAll("-$", "");
        }
        return ascii;
    }
}
