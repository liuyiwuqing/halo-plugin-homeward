package site.muyin.homeward.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class CaseImportRequest {

    @Schema(description = "来源网页地址。未提供网页正文时必填")
    private String sourceUrl;

    @Schema(description = "从网页复制的正文或 HTML。提供后不会访问来源地址", maxLength = 500_000)
    private String pageContent;
}
