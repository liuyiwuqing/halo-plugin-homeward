package site.muyin.homeward.setting;

import lombok.Data;

@Data
public class HomewardProtectionSetting {

    public static final String GROUP = "protection";

    private Boolean clueSubmissionEnabled;

    private Integer maxSubmissionsPerHour;

    private Integer minContentLength;

    private String ipHashSalt;

    public static HomewardProtectionSetting defaults() {
        var setting = new HomewardProtectionSetting();
        setting.setClueSubmissionEnabled(true);
        setting.setMaxSubmissionsPerHour(3);
        setting.setMinContentLength(10);
        setting.setIpHashSalt("homeward");
        return setting;
    }

    public boolean clueSubmissionEnabledOrDefault() {
        return clueSubmissionEnabled == null || Boolean.TRUE.equals(clueSubmissionEnabled);
    }

    public int maxSubmissionsPerHourOrDefault() {
        return maxSubmissionsPerHour == null || maxSubmissionsPerHour < 0 ? 3 : maxSubmissionsPerHour;
    }

    public int minContentLengthOrDefault() {
        return minContentLength == null || minContentLength <= 0 ? 10 : minContentLength;
    }

    public String ipHashSaltOrDefault() {
        return ipHashSalt == null || ipHashSalt.isBlank() ? "homeward" : ipHashSalt;
    }
}
