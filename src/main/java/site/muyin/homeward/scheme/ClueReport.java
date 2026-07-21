package site.muyin.homeward.scheme;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;
import site.muyin.homeward.enums.ClueStatus;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(group = "homeward.muyin.site", version = "v1alpha1",
    kind = "ClueReport", plural = "cluereports", singular = "cluereport")
public class ClueReport extends AbstractExtension {

    public static final String EXTENSION_NAME_PREFIX = "homeward-clue-";

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec = new Spec();

    @Data
    @Schema(name = "ClueReportSpec")
    public static class Spec {

        @Schema(description = "关联案例 metadata.name", requiredMode = Schema.RequiredMode.REQUIRED)
        private String caseName;

        @Schema(description = "关联案例 slug", requiredMode = Schema.RequiredMode.REQUIRED)
        private String caseSlug;

        @Schema(description = "关联案例标题")
        private String caseTitle;

        @Schema(description = "提交人姓名", maxLength = 80)
        private String submitterName;

        @Schema(description = "联系方式", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 120)
        private String contact;

        @Schema(description = "线索内容", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 3000)
        private String content;

        @Schema(description = "线索位置", maxLength = 200)
        private String location;

        @Schema(description = "IP 哈希")
        private String ipHash;

        @Schema(description = "User-Agent 摘要")
        private String userAgent;

        @Schema(description = "处理状态")
        private ClueStatus status = ClueStatus.PENDING;

        @Schema(description = "处理人")
        private String handler;

        @Schema(description = "处理备注", maxLength = 2000)
        private String processNote;

        @Schema(description = "提交时间")
        private Instant submittedAt;

        @Schema(description = "更新时间")
        private Instant updatedAt;
    }
}
