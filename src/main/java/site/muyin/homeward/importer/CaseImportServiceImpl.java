package site.muyin.homeward.importer;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import site.muyin.homeward.model.CaseImportBatchItem;
import site.muyin.homeward.model.CaseImportBatchPreview;
import site.muyin.homeward.model.CaseImportBatchRequest;
import site.muyin.homeward.model.CaseImportPreview;
import site.muyin.homeward.model.CaseImportRequest;

@Service
public class CaseImportServiceImpl implements CaseImportService {

    static final int MAX_PASTED_CONTENT_LENGTH = 500_000;

    public static final int MAX_DOCUMENT_BYTES = 10_000_000;

    public static final int MAX_BATCH_ITEMS = 20;

    private final CaseImportParser parser;

    private final WebPageFetcher webPageFetcher;

    private final Optional<CaseImportAiService> aiService;

    @Autowired
    public CaseImportServiceImpl(CaseImportParser parser, WebPageFetcher webPageFetcher,
                                 Optional<CaseImportAiService> aiService) {
        this.parser = parser;
        this.webPageFetcher = webPageFetcher;
        this.aiService = aiService == null ? Optional.empty() : aiService;
    }

    CaseImportServiceImpl(CaseImportParser parser, WebPageFetcher webPageFetcher) {
        this(parser, webPageFetcher, Optional.empty());
    }

    @Override
    public Mono<CaseImportPreview> preview(CaseImportRequest request) {
        if (request == null) {
            return Mono.error(badRequest("导入内容不能为空"));
        }
        String sourceUrl = trimToNull(request.getSourceUrl());
        String pageContent = trimToNull(request.getPageContent());
        if (sourceUrl == null && pageContent == null) {
            return Mono.error(badRequest("请填写网页地址，或粘贴网页正文"));
        }
        if (pageContent != null && pageContent.length() > MAX_PASTED_CONTENT_LENGTH) {
            return Mono.error(badRequest("粘贴的网页正文不能超过 500000 个字符"));
        }
        if (sourceUrl != null) {
            validateSourceUrl(sourceUrl);
        }
        if (pageContent != null) {
            return parseContent(request, pageContent, sourceUrl);
        }
        return webPageFetcher.fetch(sourceUrl)
            .flatMap(page -> parseContent(request, page.content(), page.sourceUrl()));
    }

    @Override
    public Mono<CaseImportPreview> previewDocument(CaseImportRequest request, byte[] content,
                                                    String mediaType, String filename) {
        if (request == null) {
            return Mono.error(badRequest("文档导入参数不能为空"));
        }
        if (content == null || content.length == 0) {
            return Mono.error(badRequest("请上传要解析的文档"));
        }
        if (content.length > MAX_DOCUMENT_BYTES) {
            return Mono.error(new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE,
                "文档不能超过 10 MB"));
        }
        if (trimToNull(mediaType) == null) {
            return Mono.error(badRequest("无法识别文档类型"));
        }
        String sourceUrl = trimToNull(request.getSourceUrl());
        if (sourceUrl != null) {
            validateSourceUrl(sourceUrl);
            request.setSourceUrl(sourceUrl);
        }
        return aiService
            .map(service -> service.previewDocument(request, content, mediaType, filename))
            .orElseGet(() -> Mono.error(aiUnavailable()));
    }

    @Override
    public Mono<CaseImportBatchPreview> previewBatch(CaseImportBatchRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            return Mono.error(badRequest("批量导入至少需要一条资料"));
        }
        if (request.getItems().size() > MAX_BATCH_ITEMS) {
            return Mono.error(badRequest("单次最多批量解析 20 条资料"));
        }
        if (aiService.isEmpty()) {
            return Mono.error(aiUnavailable());
        }
        String batchModelName = trimToNull(request.getModelName());
        return Flux.fromIterable(request.getItems())
            .index()
            .flatMapSequential(indexed -> {
                int index = Math.toIntExact(indexed.getT1());
                CaseImportRequest item = copyRequest(indexed.getT2(), batchModelName);
                if (item != null) {
                    item.setAi(true);
                }
                return preview(item)
                    .map(preview -> CaseImportBatchItem.success(index, item, preview))
                    .onErrorResume(error -> Mono.just(CaseImportBatchItem.failure(index, item,
                        errorReason(error))));
            }, 2)
            .collectList()
            .map(this::batchPreview);
    }

    private Mono<CaseImportPreview> parseContent(CaseImportRequest request, String content,
                                                  String sourceUrl) {
        Mono<CaseImportPreview> parsed = Mono.fromSupplier(() -> parser.parse(content, sourceUrl))
            .flatMap(rulePreview -> request.aiRequested()
                ? aiService
                    .map(service -> service.preview(request, content, sourceUrl, rulePreview))
                    .orElseGet(() -> Mono.error(aiUnavailable()))
                : Mono.just(rulePreview));
        return parsed.flatMap(this::attachAiAvailability);
    }

    private Mono<CaseImportPreview> attachAiAvailability(CaseImportPreview preview) {
        if (preview.isAiGenerated()) {
            preview.setAiAvailable(true);
            return Mono.just(preview);
        }
        if (aiService.isEmpty()) {
            return Mono.just(preview);
        }
        return Mono.defer(() -> {
            Mono<Boolean> availability = aiService.get().isAvailable();
            if (availability == null) {
                availability = Mono.just(false);
            }
            return availability
                .defaultIfEmpty(false)
                .onErrorReturn(false)
                .map(available -> {
                    preview.setAiAvailable(available);
                    return preview;
                });
        });
    }

    private CaseImportBatchPreview batchPreview(List<CaseImportBatchItem> items) {
        var result = new CaseImportBatchPreview();
        result.setItems(items);
        result.setSucceeded((int) items.stream().filter(item -> item.getPreview() != null).count());
        result.setFailed(items.size() - result.getSucceeded());
        return result;
    }

    private static CaseImportRequest copyRequest(CaseImportRequest source, String batchModelName) {
        if (source == null) {
            return null;
        }
        var copy = new CaseImportRequest();
        copy.setSourceUrl(source.getSourceUrl());
        copy.setPageContent(source.getPageContent());
        copy.setAi(source.getAi());
        copy.setModelName(trimToNull(source.getModelName()) == null
            ? batchModelName : source.getModelName());
        return copy;
    }

    private static String errorReason(Throwable error) {
        if (error instanceof ResponseStatusException response
            && response.getReason() != null && !response.getReason().isBlank()) {
            return response.getReason();
        }
        return "解析失败，请检查输入内容或 AI 模型配置";
    }

    private static void validateSourceUrl(String sourceUrl) {
        URI uri;
        try {
            uri = URI.create(sourceUrl);
        } catch (IllegalArgumentException ignored) {
            throw badRequest("网页地址必须是有效的 HTTP 或 HTTPS 地址");
        }
        String scheme = uri.getScheme();
        if (uri.getHost() == null
            || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw badRequest("网页地址必须是有效的 HTTP 或 HTTPS 地址");
        }
    }

    static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException aiUnavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "AI Foundation 未启用或未配置可用的语言模型");
    }
}
