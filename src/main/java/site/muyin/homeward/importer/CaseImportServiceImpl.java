package site.muyin.homeward.importer;

import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import site.muyin.homeward.model.CaseImportPreview;
import site.muyin.homeward.model.CaseImportRequest;

@Service
@RequiredArgsConstructor
public class CaseImportServiceImpl implements CaseImportService {

    static final int MAX_PASTED_CONTENT_LENGTH = 500_000;

    private final CaseImportParser parser;

    private final WebPageFetcher webPageFetcher;

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
            String finalSourceUrl = sourceUrl;
            return Mono.fromSupplier(() -> parser.parse(pageContent, finalSourceUrl));
        }
        return webPageFetcher.fetch(sourceUrl)
            .map(page -> parser.parse(page.content(), page.sourceUrl()));
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

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
