package site.muyin.homeward.setting;

import lombok.Data;

@Data
public class HomewardDisplaySetting {

    public static final String GROUP = "display";

    private Integer widgetCaseLimit;

    private Integer publicPageCaseLimit;

    private Boolean inject404;

    public static HomewardDisplaySetting defaults() {
        var setting = new HomewardDisplaySetting();
        setting.setWidgetCaseLimit(4);
        setting.setPublicPageCaseLimit(20);
        setting.setInject404(true);
        return setting;
    }

    public int widgetCaseLimitOrDefault() {
        return widgetCaseLimit == null || widgetCaseLimit <= 0 ? 4 : Math.min(widgetCaseLimit, 12);
    }

    public int publicPageCaseLimitOrDefault() {
        return publicPageCaseLimit == null || publicPageCaseLimit <= 0
            ? 20
            : Math.min(publicPageCaseLimit, 50);
    }

    public boolean inject404OrDefault() {
        return inject404 == null || Boolean.TRUE.equals(inject404);
    }
}
