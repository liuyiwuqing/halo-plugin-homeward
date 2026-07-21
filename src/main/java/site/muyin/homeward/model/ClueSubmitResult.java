package site.muyin.homeward.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClueSubmitResult {

    private String status;

    private String message;
}
