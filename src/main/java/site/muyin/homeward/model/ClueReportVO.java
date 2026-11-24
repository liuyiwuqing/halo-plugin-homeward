package site.muyin.homeward.model;

import lombok.Data;
import site.muyin.homeward.enums.ClueStatus;
import site.muyin.homeward.scheme.ClueReport;

import java.time.Instant;
import java.util.List;

@Data
public class ClueReportVO {

    private String name;

    private String caseName;

    private String caseSlug;

    private String caseTitle;

    private String submitterName;

    private String contact;

    private String content;

    private String location;

    private String ipHash;

    private String userAgent;

    private ClueStatus status;

    private String handler;

    private String processNote;

    private Instant submittedAt;

    private Instant updatedAt;

    private List<ClueReport.ProcessingRecord> processingHistory;

    public static ClueReportVO from(ClueReport source) {
        var vo = new ClueReportVO();
        vo.setName(source.getMetadata() == null ? null : source.getMetadata().getName());
        var spec = source.getSpec();
        if (spec == null) {
            return vo;
        }
        vo.setCaseName(spec.getCaseName());
        vo.setCaseSlug(spec.getCaseSlug());
        vo.setCaseTitle(spec.getCaseTitle());
        vo.setSubmitterName(spec.getSubmitterName());
        vo.setContact(spec.getContact());
        vo.setContent(spec.getContent());
        vo.setLocation(spec.getLocation());
        vo.setIpHash(spec.getIpHash());
        vo.setUserAgent(spec.getUserAgent());
        vo.setStatus(spec.getStatus());
        vo.setHandler(spec.getHandler());
        vo.setProcessNote(spec.getProcessNote());
        vo.setSubmittedAt(spec.getSubmittedAt());
        vo.setUpdatedAt(spec.getUpdatedAt());
        vo.setProcessingHistory(spec.getProcessingHistory() == null
            ? List.of() : List.copyOf(spec.getProcessingHistory()));
        return vo;
    }
}
