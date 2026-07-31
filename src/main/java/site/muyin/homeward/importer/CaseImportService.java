package site.muyin.homeward.importer;

import reactor.core.publisher.Mono;
import site.muyin.homeward.model.CaseImportBatchPreview;
import site.muyin.homeward.model.CaseImportBatchRequest;
import site.muyin.homeward.model.CaseImportPreview;
import site.muyin.homeward.model.CaseImportRequest;

public interface CaseImportService {

    Mono<CaseImportPreview> preview(CaseImportRequest request);

    Mono<CaseImportPreview> previewDocument(CaseImportRequest request, byte[] content,
                                            String mediaType, String filename);

    Mono<CaseImportBatchPreview> previewBatch(CaseImportBatchRequest request);
}
