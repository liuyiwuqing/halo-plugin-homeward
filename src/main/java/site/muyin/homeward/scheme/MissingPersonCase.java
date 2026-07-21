package site.muyin.homeward.scheme;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;
import site.muyin.homeward.enums.CaseStatus;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(group = "homeward.muyin.site", version = "v1alpha1",
    kind = "MissingPersonCase", plural = "missingpersoncases", singular = "missingpersoncase")
public class MissingPersonCase extends AbstractExtension {

    public static final String EXTENSION_NAME_PREFIX = "homeward-case-";

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec = new Spec();

    @Data
    @Schema(name = "MissingPersonCaseSpec")
    public static class Spec {

        @Schema(description = "公开访问标识", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 120)
        private String slug;

        @Schema(description = "展示标题", maxLength = 120)
        private String title;

        @Schema(description = "姓名", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 80)
        private String personName;

        @Schema(description = "性别", maxLength = 20)
        private String gender;

        @Schema(description = "出生日期", maxLength = 40)
        private String birthDate;

        @Schema(description = "失踪日期", maxLength = 40)
        private String missingDate;

        @Schema(description = "失踪年龄", minimum = "0", maximum = "150")
        private Integer missingAge;

        @Schema(description = "当前年龄", minimum = "0", maximum = "150")
        private Integer currentAge;

        @Schema(description = "失踪地点", maxLength = 200)
        private String missingLocation;

        @Schema(description = "籍贯/家乡", maxLength = 200)
        private String hometown;

        @Schema(description = "封面图")
        private String coverImage;

        @Schema(description = "相册")
        private List<String> gallery = new ArrayList<>();

        @Schema(description = "体貌特征", maxLength = 1000)
        private String physicalFeatures;

        @Schema(description = "详细描述", maxLength = 4000)
        private String description;

        @Schema(description = "联系人", maxLength = 80)
        private String contactName;

        @Schema(description = "联系电话", maxLength = 80)
        private String contactPhone;

        @Schema(description = "信息来源", maxLength = 120)
        private String sourceName;

        @Schema(description = "来源链接")
        private String sourceUrl;

        @Schema(description = "状态")
        private CaseStatus status = CaseStatus.DRAFT;

        @Schema(description = "展示优先级")
        private Integer priority = 0;

        @Schema(description = "发布时间")
        private Instant publishedAt;

        @Schema(description = "找到时间")
        private Instant foundAt;

        @Schema(description = "创建时间")
        private Instant createdAt;

        @Schema(description = "更新时间")
        private Instant updatedAt;
    }
}
