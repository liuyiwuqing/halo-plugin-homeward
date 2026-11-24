package site.muyin.homeward.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;

@Data
@Builder
public class ProtectionCheckResult {

    private boolean allowed;

    private String reason;

    private HttpStatus rejectionStatus;

    public static ProtectionCheckResult allowed() {
        return ProtectionCheckResult.builder().allowed(true).build();
    }

    public static ProtectionCheckResult rejected(String reason) {
        return rejected(HttpStatus.BAD_REQUEST, reason);
    }

    public static ProtectionCheckResult rejected(HttpStatus status, String reason) {
        return ProtectionCheckResult.builder().allowed(false).reason(reason)
            .rejectionStatus(status).build();
    }
}
