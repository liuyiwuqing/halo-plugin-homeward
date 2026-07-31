package site.muyin.homeward.importer;

import reactor.core.publisher.Mono;
import site.muyin.homeward.model.CaseImportPreview;
import site.muyin.homeward.model.CaseImportRequest;

/**
 * Internal seam between the regular importer and the optional business implementation that calls
 * AI Foundation. Keeping the interface free of SDK types makes the validation and batch flow easy
 * to test and keeps all AI-specific code in one place.
 */
public interface CaseImportAiService {

    /**
     * Checks whether the configured default language model can be resolved without generating
     * content.
     */
    Mono<Boolean> isAvailable();

    Mono<CaseImportPreview> preview(CaseImportRequest request, String content, String sourceUrl,
                                    CaseImportPreview rulePreview);

    Mono<CaseImportPreview> previewDocument(CaseImportRequest request, byte[] content,
                                            String mediaType, String filename);
}
