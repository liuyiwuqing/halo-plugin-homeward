package site.muyin.homeward.setting;

import java.util.List;
import lombok.Data;

@Data
public class HomewardNotificationSetting {

    public static final String GROUP = "notification";

    private Boolean enabled;

    private List<String> recipients;

    public static HomewardNotificationSetting defaults() {
        var setting = new HomewardNotificationSetting();
        setting.setEnabled(true);
        setting.setRecipients(List.of("admin"));
        return setting;
    }

    public boolean enabledOrDefault() {
        return enabled == null || Boolean.TRUE.equals(enabled);
    }

    public List<String> recipientsOrDefault() {
        if (recipients == null || recipients.isEmpty()) {
            return List.of("admin");
        }
        return recipients.stream()
            .filter(item -> item != null && !item.isBlank())
            .map(String::trim)
            .distinct()
            .toList();
    }
}
