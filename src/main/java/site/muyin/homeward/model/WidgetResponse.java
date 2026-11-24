package site.muyin.homeward.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WidgetResponse {

    private boolean enabled;

    private boolean clueSubmissionEnabled;

    private int minContentLength;

    private int pageSize;

    private String title;

    private String subtitle;

    private String contactText;

    private String publicPageUrl;

    private List<MissingPersonCasePublicVO> cases;
}
