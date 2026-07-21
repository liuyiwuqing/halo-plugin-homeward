package site.muyin.homeward.model;

import java.time.Instant;
import java.util.List;
import lombok.Data;
import site.muyin.homeward.enums.CaseStatus;
import site.muyin.homeward.scheme.MissingPersonCase;

@Data
public class MissingPersonCaseVO {

    private String name;

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

    private String contactPhone;

    private String sourceName;

    private String sourceUrl;

    private CaseStatus status;

    private Integer priority;

    private Instant publishedAt;

    private Instant foundAt;

    private Instant createdAt;

    private Instant updatedAt;

    public static MissingPersonCaseVO from(MissingPersonCase source) {
        var vo = new MissingPersonCaseVO();
        vo.setName(source.getMetadata() == null ? null : source.getMetadata().getName());
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
        vo.setContactPhone(spec.getContactPhone());
        vo.setSourceName(spec.getSourceName());
        vo.setSourceUrl(spec.getSourceUrl());
        vo.setStatus(spec.getStatus());
        vo.setPriority(spec.getPriority());
        vo.setPublishedAt(spec.getPublishedAt());
        vo.setFoundAt(spec.getFoundAt());
        vo.setCreatedAt(spec.getCreatedAt());
        vo.setUpdatedAt(spec.getUpdatedAt());
        return vo;
    }
}
