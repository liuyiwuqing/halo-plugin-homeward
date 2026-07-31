package site.muyin.homeward.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CaseImportBatchPreview {

    private List<CaseImportBatchItem> items = new ArrayList<>();

    private int succeeded;

    private int failed;
}
