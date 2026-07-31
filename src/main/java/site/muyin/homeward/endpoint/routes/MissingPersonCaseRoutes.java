package site.muyin.homeward.endpoint.routes;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springdoc.core.fn.builders.schema.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListResult;
import site.muyin.homeward.enums.CaseStatus;
import site.muyin.homeward.importer.CaseImportService;
import site.muyin.homeward.importer.CaseImportServiceImpl;
import site.muyin.homeward.model.CaseImportBatchItem;
import site.muyin.homeward.model.CaseImportBatchPreview;
import site.muyin.homeward.model.CaseImportBatchRequest;
import site.muyin.homeward.model.CaseImportPreview;
import site.muyin.homeward.model.CaseImportRequest;
import site.muyin.homeward.model.MissingPersonCasePublicVO;
import site.muyin.homeward.model.MissingPersonCaseVO;
import site.muyin.homeward.model.WidgetResponse;
import site.muyin.homeward.query.MissingPersonCaseQuery;
import site.muyin.homeward.scheme.MissingPersonCase;
import site.muyin.homeward.service.MissingPersonCaseService;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static site.muyin.homeward.endpoint.HomewardConsoleEndpoint.CONSOLE_TAG;

@Component
@RequiredArgsConstructor
public class MissingPersonCaseRoutes {

    private final MissingPersonCaseService service;

    private final CaseImportService caseImportService;

    public RouterFunction<ServerResponse> consoleRoutes() {
        return SpringdocRouteBuilder.route()
            .GET("/list", this::listForConsole, builder -> {
                builder.operationId("homeward-case-list-for-console")
                    .description("寻亲案例列表")
                    .tag(CONSOLE_TAG)
                    .response(responseBuilder()
                        .implementation(ListResult.generateGenericClass(MissingPersonCaseVO.class)));
                MissingPersonCaseQuery.buildParameters(builder);
            })
            .GET("/{name}", this::getForConsole, builder -> builder
                .operationId("homeward-case-detail-for-console")
                .description("寻亲案例详情")
                .tag(CONSOLE_TAG)
                .parameter(parameterBuilder().name("name").in(ParameterIn.PATH).required(true)
                    .implementation(String.class))
                .response(responseBuilder().implementation(MissingPersonCaseVO.class)))
            .POST("/create", this::createForConsole, builder -> builder
                .operationId("homeward-case-create-for-console")
                .description("创建寻亲案例")
                .tag(CONSOLE_TAG)
                .requestBody(requestBodyBuilder().required(true)
                    .content(contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE)
                        .schema(Builder.schemaBuilder().implementation(MissingPersonCase.class))))
                .response(responseBuilder().implementation(MissingPersonCaseVO.class)))
            .POST("/update", this::updateForConsole, builder -> builder
                .operationId("homeward-case-update-for-console")
                .description("更新寻亲案例")
                .tag(CONSOLE_TAG)
                .requestBody(requestBodyBuilder().required(true)
                    .content(contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE)
                        .schema(Builder.schemaBuilder().implementation(MissingPersonCase.class))))
                .response(responseBuilder().implementation(MissingPersonCaseVO.class)))
            .POST("/import/preview", this::previewImportForConsole, builder -> builder
                .operationId("homeward-case-import-preview-for-console")
                .description("从公网网页或粘贴正文解析寻亲案例草稿，可选 AI 结构化提取，不直接保存")
                .tag(CONSOLE_TAG)
                .requestBody(requestBodyBuilder().required(true)
                    .content(contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE)
                        .schema(Builder.schemaBuilder().implementation(CaseImportRequest.class))))
                .response(responseBuilder().implementation(CaseImportPreview.class)))
            .POST("/import/preview/document", this::previewDocumentImportForConsole,
                builder -> builder
                    .operationId("homeward-case-import-document-preview-for-console")
                    .description("通过 AI 解析 PDF、Word、文本等文档，生成待核对寻亲案例草稿")
                    .tag(CONSOLE_TAG))
            .POST("/import/batch/preview", this::previewBatchImportForConsole, builder -> builder
                .operationId("homeward-case-import-batch-preview-for-console")
                .description("批量使用 AI 解析网页地址或文本资料，逐条返回可核对草稿")
                .tag(CONSOLE_TAG)
                .requestBody(requestBodyBuilder().required(true)
                    .content(contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE)
                        .schema(Builder.schemaBuilder()
                            .implementation(CaseImportBatchRequest.class))))
                .response(responseBuilder().implementation(CaseImportBatchPreview.class)))
            .POST("/import/batch/preview/documents", this::previewBatchDocumentsForConsole,
                builder -> builder
                    .operationId("homeward-case-import-batch-document-preview-for-console")
                    .description("批量使用 AI 解析上传的文档，逐条返回可核对草稿")
                    .tag(CONSOLE_TAG))
            .POST("/{name}/publish", request -> status(request, CaseStatus.PUBLISHED), builder -> builder
                .operationId("homeward-case-publish-for-console")
                .description("发布寻亲案例")
                .tag(CONSOLE_TAG))
            .POST("/{name}/mark-found", request -> status(request, CaseStatus.FOUND), builder -> builder
                .operationId("homeward-case-mark-found-for-console")
                .description("标记已找到")
                .tag(CONSOLE_TAG))
            .POST("/{name}/archive", request -> status(request, CaseStatus.ARCHIVED), builder -> builder
                .operationId("homeward-case-archive-for-console")
                .description("归档寻亲案例")
                .tag(CONSOLE_TAG))
            .DELETE("/{name}/delete", this::deleteForConsole, builder -> builder
                .operationId("homeward-case-delete-for-console")
                .description("删除寻亲案例")
                .tag(CONSOLE_TAG))
            .build();
    }

    public Mono<ServerResponse> widget(ServerRequest request) {
        boolean publicPage = request.queryParam("surface")
            .map("page"::equalsIgnoreCase)
            .orElse(false);
        return service.widget(publicPage)
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    public Mono<ServerResponse> detailForPublic(ServerRequest request) {
        return service.getPublishedBySlug(request.pathVariable("slug"))
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private Mono<ServerResponse> listForConsole(ServerRequest request) {
        return service.listForConsole(new MissingPersonCaseQuery(request.exchange()))
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private Mono<ServerResponse> getForConsole(ServerRequest request) {
        return service.getForConsole(request.pathVariable("name"))
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private Mono<ServerResponse> createForConsole(ServerRequest request) {
        return request.bodyToMono(MissingPersonCase.class)
            .flatMap(service::create)
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private Mono<ServerResponse> updateForConsole(ServerRequest request) {
        return request.bodyToMono(MissingPersonCase.class)
            .flatMap(service::update)
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private Mono<ServerResponse> previewImportForConsole(ServerRequest request) {
        return request.bodyToMono(CaseImportRequest.class)
            .flatMap(caseImportService::preview)
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private Mono<ServerResponse> previewDocumentImportForConsole(ServerRequest request) {
        return request.multipartData()
            .flatMap(parts -> {
                FilePart file = firstFile(parts);
                if (file == null) {
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "请上传要解析的文档"));
                }
                String mediaType = mediaType(file);
                validateMediaType(mediaType);
                return readFile(file)
                    .flatMap(content -> caseImportService.previewDocument(documentRequest(parts),
                        content, mediaType, safeFilename(file.filename())))
                    .flatMap(response -> ServerResponse.ok().bodyValue(response));
            });
    }

    private Mono<ServerResponse> previewBatchImportForConsole(ServerRequest request) {
        return request.bodyToMono(CaseImportBatchRequest.class)
            .flatMap(caseImportService::previewBatch)
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private Mono<ServerResponse> previewBatchDocumentsForConsole(ServerRequest request) {
        return request.multipartData()
            .flatMap(parts -> {
                List<FilePart> files = fileParts(parts);
                if (files.isEmpty()) {
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "请至少上传一个要解析的文档"));
                }
                if (files.size() > CaseImportServiceImpl.MAX_BATCH_ITEMS) {
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "单次最多批量解析 20 个文档"));
                }
                return Flux.fromIterable(files)
                    .index()
                    .flatMapSequential(indexed -> {
                        int index = Math.toIntExact(indexed.getT1());
                        FilePart file = indexed.getT2();
                        String mediaType = mediaType(file);
                        try {
                            validateMediaType(mediaType);
                        } catch (ResponseStatusException error) {
                            return Mono.just(CaseImportBatchItem.failure(index,
                                documentRequest(parts), error.getReason()));
                        }
                        return readFile(file)
                            .flatMap(content -> caseImportService.previewDocument(
                                documentRequest(parts), content, mediaType,
                                safeFilename(file.filename())))
                            .map(preview -> CaseImportBatchItem.success(index,
                                documentRequest(parts), preview))
                            .onErrorResume(error -> Mono.just(CaseImportBatchItem.failure(index,
                                documentRequest(parts), errorReason(error))));
                    }, 2)
                    .collectList()
                    .map(MissingPersonCaseRoutes::batchPreview)
                    .flatMap(response -> ServerResponse.ok().bodyValue(response));
            });
    }

    private static CaseImportBatchPreview batchPreview(List<CaseImportBatchItem> items) {
        var result = new CaseImportBatchPreview();
        result.setItems(items);
        result.setSucceeded((int) items.stream().filter(item -> item.getPreview() != null).count());
        result.setFailed(items.size() - result.getSucceeded());
        return result;
    }

    private static Mono<byte[]> readFile(FilePart file) {
        return DataBufferUtils.join(file.content(), CaseImportServiceImpl.MAX_DOCUMENT_BYTES)
            .map(buffer -> {
                try {
                    byte[] content = new byte[buffer.readableByteCount()];
                    buffer.read(content);
                    return content;
                } finally {
                    DataBufferUtils.release(buffer);
                }
            })
            .onErrorMap(DataBufferLimitException.class, error -> new ResponseStatusException(
                HttpStatus.CONTENT_TOO_LARGE, "文档不能超过 10 MB", error));
    }

    private static FilePart firstFile(MultiValueMap<String, Part> parts) {
        return fileParts(parts).stream().findFirst().orElse(null);
    }

    private static List<FilePart> fileParts(MultiValueMap<String, Part> parts) {
        var result = new ArrayList<FilePart>();
        for (String name : List.of("file", "files")) {
            for (Part part : parts.getOrDefault(name, List.of())) {
                if (part instanceof FilePart file) {
                    result.add(file);
                }
            }
        }
        return result;
    }

    private static CaseImportRequest documentRequest(MultiValueMap<String, Part> parts) {
        var request = new CaseImportRequest();
        request.setAi(true);
        request.setModelName(formValue(parts, "modelName"));
        request.setSourceUrl(formValue(parts, "sourceUrl"));
        return request;
    }

    private static String formValue(MultiValueMap<String, Part> parts, String name) {
        Part part = parts.getFirst(name);
        return part instanceof FormFieldPart field ? field.value() : null;
    }

    private static String mediaType(FilePart file) {
        String filename = safeFilename(file.filename()).toLowerCase(Locale.ROOT);
        if (file.headers().getContentType() != null) {
            String value = file.headers().getContentType().toString().toLowerCase(Locale.ROOT);
            int separator = value.indexOf(';');
            value = separator >= 0 ? value.substring(0, separator).trim() : value;
            if (!value.isBlank() && !"application/octet-stream".equals(value)) {
                return value;
            }
        }
        if (filename.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (filename.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (filename.endsWith(".doc")) {
            return "application/msword";
        }
        if (filename.endsWith(".md")) {
            return "text/markdown";
        }
        if (filename.endsWith(".html") || filename.endsWith(".htm")) {
            return "text/html";
        }
        if (filename.endsWith(".json")) {
            return "application/json";
        }
        if (filename.endsWith(".rtf")) {
            return "application/rtf";
        }
        return filename.endsWith(".txt") ? "text/plain" : "application/octet-stream";
    }

    private static void validateMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()
            || !(mediaType.startsWith("text/") || mediaType.equals("application/pdf")
                || mediaType.equals("application/msword")
                || mediaType.equals("application/rtf")
                || mediaType.equals("application/json")
                || mediaType.equals("application/xml")
                || mediaType.equals(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "仅支持 PDF、Word、TXT、Markdown、HTML 等可解析文档");
        }
    }

    private static String safeFilename(String filename) {
        return filename == null || filename.isBlank() ? "uploaded-document" : filename.trim();
    }

    private static String errorReason(Throwable error) {
        if (error instanceof ResponseStatusException response
            && response.getReason() != null && !response.getReason().isBlank()) {
            return response.getReason();
        }
        return "解析失败，请检查文档内容或 AI 模型配置";
    }

    private Mono<ServerResponse> status(ServerRequest request, CaseStatus status) {
        return service.updateStatus(request.pathVariable("name"), status)
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private Mono<ServerResponse> deleteForConsole(ServerRequest request) {
        return service.delete(request.pathVariable("name"))
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }
}
