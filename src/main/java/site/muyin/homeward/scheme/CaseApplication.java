package site.muyin.homeward.scheme;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

/** 申请与公开案例分开保存，未经审核的资料不会进入公开查询。 */
@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "homeward.muyin.site", version = "v1alpha1",
    kind = "CaseApplication", plural = "caseapplications", singular = "caseapplication")
public class CaseApplication extends AbstractExtension {
    private Spec spec = new Spec();

    public enum Status { PENDING, CONVERTING, ACCEPTED, REJECTED }

    @Data
    @Schema(name = "CaseApplicationSpec")
    public static class Spec {
        @Schema(maxLength = 80) private String personName;
        @Schema(maxLength = 40) private String missingDate;
        @Schema(maxLength = 200) private String missingLocation;
        @Schema(maxLength = 4000) private String description;
        @Schema(maxLength = 80) private String applicantName;
        @Schema(maxLength = 80) private String relationship;
        @Schema(maxLength = 120) private String contact;
        @Schema(maxLength = 1000) private String sourceUrl;
        private Status status = Status.PENDING;
        private Instant consentAt;
        private Instant submittedAt;
        private Instant updatedAt;
        private String handler;
        @Schema(maxLength = 2000) private String reviewNote;
        private String draftName;
    }
}
