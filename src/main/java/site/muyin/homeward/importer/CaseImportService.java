package site.muyin.homeward.importer;

import reactor.core.publisher.Mono;
import site.muyin.homeward.model.CaseImportPreview;
import site.muyin.homeward.model.CaseImportRequest;

public interface CaseImportService {

    Mono<CaseImportPreview> preview(CaseImportRequest request);
}
