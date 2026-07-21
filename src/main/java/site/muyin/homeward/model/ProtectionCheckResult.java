package site.muyin.homeward.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProtectionCheckResult {

    private boolean allowed;

    private String reason;

    public static ProtectionCheckResult allowed() {
        return ProtectionCheckResult.builder().allowed(true).build();
    }

    public static ProtectionCheckResult rejected(String reason) {
        return ProtectionCheckResult.builder().allowed(false).reason(reason).build();
    }
}
