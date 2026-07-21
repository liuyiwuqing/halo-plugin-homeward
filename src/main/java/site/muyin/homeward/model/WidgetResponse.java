package site.muyin.homeward.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WidgetResponse {

    private boolean enabled;

    private String title;

    private String subtitle;

    private String contactText;

    private String publicPageUrl;

    private List<MissingPersonCasePublicVO> cases;
}
