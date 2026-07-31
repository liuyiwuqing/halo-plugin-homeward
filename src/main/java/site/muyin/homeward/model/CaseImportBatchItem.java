package site.muyin.homeward.model;

import lombok.Data;

@Data
public class CaseImportBatchItem {

    private int index;

    private String sourceUrl;

    private CaseImportPreview preview;

    private String error;

    public static CaseImportBatchItem success(int index, CaseImportRequest request,
                                              CaseImportPreview preview) {
        var item = new CaseImportBatchItem();
        item.setIndex(index);
        item.setSourceUrl(request == null ? null : request.getSourceUrl());
        item.setPreview(preview);
        return item;
    }

    public static CaseImportBatchItem failure(int index, CaseImportRequest request, String error) {
        var item = new CaseImportBatchItem();
        item.setIndex(index);
        item.setSourceUrl(request == null ? null : request.getSourceUrl());
        item.setError(error);
        return item;
    }
}
