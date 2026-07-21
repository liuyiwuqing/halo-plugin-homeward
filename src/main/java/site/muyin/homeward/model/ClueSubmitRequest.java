package site.muyin.homeward.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class ClueSubmitRequest {

    @Schema(description = "提交人姓名", maxLength = 80)
    private String submitterName;

    @Schema(description = "联系方式", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 120)
    private String contact;

    @Schema(description = "线索内容", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 3000)
    private String content;

    @Schema(description = "线索位置", maxLength = 200)
    private String location;

    @Schema(description = "蜜罐字段，正常访客应为空")
    private String website;
}
