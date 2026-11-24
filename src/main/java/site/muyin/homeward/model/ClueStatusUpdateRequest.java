package site.muyin.homeward.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import site.muyin.homeward.enums.ClueStatus;

@Data
@JsonIgnoreProperties("handler")
public class ClueStatusUpdateRequest {

    private ClueStatus status;

    private String processNote;
}
