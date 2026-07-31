package site.muyin.homeward.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class CaseImportRequest {

    @Schema(description = "来源网页地址。未提供网页正文时必填")
    private String sourceUrl;

    @Schema(description = "从网页复制的正文或 HTML。提供后不会访问来源地址", maxLength = 500_000)
    private String pageContent;

    @Schema(description = "是否使用 AI Foundation 进行语义解析。默认关闭，批量解析接口会自动开启")
    private Boolean ai;

    @Schema(description = "AI 模型资源名，对应 AiModel.metadata.name。留空使用 AI Foundation 默认模型")
    private String modelName;

    public boolean aiRequested() {
        return Boolean.TRUE.equals(ai);
    }
}
