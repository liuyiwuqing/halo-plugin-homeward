package site.muyin.homeward.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CaseImportPreview {

    private MissingPersonCaseVO caseData = new MissingPersonCaseVO();

    private int extractedFieldCount;

    private List<String> warnings = new ArrayList<>();
}
