package site.muyin.homeward.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CaseImportBatchRequest {

    @Schema(description = "待解析的网页或文本资料，最多由插件配置限制")
    private List<CaseImportRequest> items = new ArrayList<>();

    @Schema(description = "批量任务使用的 AI 模型资源名；单项 modelName 优先")
    private String modelName;
}
