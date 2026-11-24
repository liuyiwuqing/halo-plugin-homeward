package site.muyin.homeward.model;

import lombok.Data;

@Data
public class CaseApplicationRequest {
    private String personName;
    private String missingDate;
    private String missingLocation;
    private String description;
    private String applicantName;
    private String relationship;
    private String contact;
    private String sourceUrl;
    private Boolean consent;
    private String website;
}
