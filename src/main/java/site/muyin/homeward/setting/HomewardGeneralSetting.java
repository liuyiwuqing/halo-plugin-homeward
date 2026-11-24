package site.muyin.homeward.setting;

import lombok.Data;

@Data
public class HomewardGeneralSetting {

    public static final String GROUP = "general";

    private Boolean enabled;

    private Boolean applicationSubmissionEnabled;

    private String publicPageTitle;

    private String publicPageSubtitle;

    private String contactText;

    public static HomewardGeneralSetting defaults() {
        var setting = new HomewardGeneralSetting();
        setting.setEnabled(true);
        setting.setApplicationSubmissionEnabled(false);
        setting.setPublicPageTitle("归航寻亲");
        setting.setPublicPageSubtitle("每一次迷路，都值得被照亮。");
        setting.setContactText("如有线索，请通过表单提交，站长会尽快核验处理。");
        return setting;
    }

    public boolean enabledOrDefault() {
        return enabled == null || Boolean.TRUE.equals(enabled);
    }
}
