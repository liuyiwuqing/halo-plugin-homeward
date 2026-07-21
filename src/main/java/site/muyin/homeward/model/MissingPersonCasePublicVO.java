package site.muyin.homeward.model;

import java.time.Instant;
import java.util.List;
import lombok.Data;
import site.muyin.homeward.scheme.MissingPersonCase;

@Data
public class MissingPersonCasePublicVO {

    private String slug;

    private String title;

    private String personName;

    private String gender;

    private String birthDate;

    private String missingDate;

    private Integer missingAge;

    private Integer currentAge;

    private String missingLocation;

    private String hometown;

    private String coverImage;

    private List<String> gallery;

    private String physicalFeatures;

    private String description;

    private String contactName;

    private String sourceName;

    private String sourceUrl;

    private Instant publishedAt;

    public static MissingPersonCasePublicVO from(MissingPersonCase source) {
        var vo = new MissingPersonCasePublicVO();
        var spec = source.getSpec();
        if (spec == null) {
            return vo;
        }
        vo.setSlug(spec.getSlug());
        vo.setTitle(spec.getTitle());
        vo.setPersonName(spec.getPersonName());
        vo.setGender(spec.getGender());
        vo.setBirthDate(spec.getBirthDate());
        vo.setMissingDate(spec.getMissingDate());
        vo.setMissingAge(spec.getMissingAge());
        vo.setCurrentAge(spec.getCurrentAge());
        vo.setMissingLocation(spec.getMissingLocation());
        vo.setHometown(spec.getHometown());
        vo.setCoverImage(spec.getCoverImage());
        vo.setGallery(spec.getGallery());
        vo.setPhysicalFeatures(spec.getPhysicalFeatures());
        vo.setDescription(spec.getDescription());
        vo.setContactName(spec.getContactName());
        vo.setSourceName(spec.getSourceName());
        vo.setSourceUrl(spec.getSourceUrl());
        vo.setPublishedAt(spec.getPublishedAt());
        return vo;
    }
}
