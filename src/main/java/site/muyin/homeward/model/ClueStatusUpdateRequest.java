package site.muyin.homeward.model;

import lombok.Data;
import site.muyin.homeward.enums.ClueStatus;

@Data
public class ClueStatusUpdateRequest {

    private ClueStatus status;

    private String handler;

    private String processNote;
}
