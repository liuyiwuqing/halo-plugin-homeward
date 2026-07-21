package site.muyin.homeward.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardStats {

    private long totalCases;

    private long publishedCases;

    private long foundCases;

    private long pendingClues;
}
