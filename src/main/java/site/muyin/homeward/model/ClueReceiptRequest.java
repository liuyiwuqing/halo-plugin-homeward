package site.muyin.homeward.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class ClueReceiptRequest {
    @Schema(description = "提交成功时获得的回执凭证", minLength = 43, maxLength = 43)
    private String receiptToken;
}
