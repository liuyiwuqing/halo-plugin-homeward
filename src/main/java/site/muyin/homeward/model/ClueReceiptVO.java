package site.muyin.homeward.model;

import java.time.Instant;
import site.muyin.homeward.enums.ClueStatus;

/** 公开回执仅返回这些字段，不复用包含线索隐私的 Console VO。 */
public record ClueReceiptVO(ClueStatus status, Instant submittedAt, Instant updatedAt,
                            String message) {
}
