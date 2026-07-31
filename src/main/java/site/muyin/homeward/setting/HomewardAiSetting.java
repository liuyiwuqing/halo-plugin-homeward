package site.muyin.homeward.setting;

import lombok.Data;

@Data
public class HomewardAiSetting {

    public static final String GROUP = "ai";

    private Boolean enabled;

    /**
     * AiModel.metadata.name. A blank value means the AI Foundation default model.
     */
    private String modelName;

    /**
     * Optional model dedicated to binary document input.
     */
    private String documentModelName;

    public static HomewardAiSetting defaults() {
        var setting = new HomewardAiSetting();
        setting.setEnabled(true);
        return setting;
    }

    public boolean enabledOrDefault() {
        return enabled == null || Boolean.TRUE.equals(enabled);
    }

}
