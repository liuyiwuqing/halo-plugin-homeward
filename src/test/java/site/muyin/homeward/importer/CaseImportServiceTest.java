package site.muyin.homeward.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import site.muyin.homeward.enums.CaseStatus;
import site.muyin.homeward.model.CaseImportBatchRequest;
import site.muyin.homeward.model.CaseImportRequest;
import site.muyin.homeward.model.CaseImportPreview;
import site.muyin.homeward.model.MissingPersonCaseVO;

class CaseImportServiceTest {

    @Test
    void pastedPageContentBecomesReviewableDraftWithoutNetworkRequest() {
        var fetched = new AtomicBoolean();
        WebPageFetcher fetcher = sourceUrl -> {
            fetched.set(true);
            return Mono.error(new AssertionError("粘贴正文时不应发起网络请求"));
        };
        var service = new CaseImportServiceImpl(
            new CaseImportParser(new ObjectMapper()), fetcher);
        var request = new CaseImportRequest();
        request.setSourceUrl("https://example.org/cases/lin-xiaoman");
        request.setPageContent("""
            <html>
              <head><title>寻找林小满 - 公益寻亲网</title></head>
              <body>
                <table>
                  <tr><th>姓名</th><td>林小满</td></tr>
                  <tr><th>性别</th><td>女</td></tr>
                  <tr><th>出生日期</th><td>2012年3月4日</td></tr>
                  <tr><th>失踪日期</th><td>2018-06-07</td></tr>
                  <tr><th>失踪地点</th><td>辽宁省沈阳市和平区</td></tr>
                  <tr><th>体貌特征</th><td>左眉上方有一颗小痣</td></tr>
                </table>
              </body>
            </html>
            """);

        var preview = service.preview(request).block();

        assertThat(fetched).isFalse();
        assertThat(preview).isNotNull();
        assertThat(preview.getCaseData().getPersonName()).isEqualTo("林小满");
        assertThat(preview.getCaseData().getGender()).isEqualTo("女");
        assertThat(preview.getCaseData().getBirthDate()).isEqualTo("2012-03-04");
        assertThat(preview.getCaseData().getMissingDate()).isEqualTo("2018-06-07");
        assertThat(preview.getCaseData().getMissingLocation()).isEqualTo("辽宁省沈阳市和平区");
        assertThat(preview.getCaseData().getPhysicalFeatures()).isEqualTo("左眉上方有一颗小痣");
        assertThat(preview.getCaseData().getSourceUrl())
            .isEqualTo("https://example.org/cases/lin-xiaoman");
        assertThat(preview.getCaseData().getStatus()).isEqualTo(CaseStatus.DRAFT);
    }

    @Test
    void publicUrlFetchesPageAndReadsStructuredPersonData() {
        var requestedUrl = new AtomicReference<String>();
        WebPageFetcher fetcher = sourceUrl -> {
            requestedUrl.set(sourceUrl);
            return Mono.just(new FetchedWebPage("https://www.example.org/missing/chen-an", """
                <html>
                  <head>
                    <meta property="og:site_name" content="公益寻亲网">
                    <meta property="og:title" content="寻找陈安">
                    <script type="application/ld+json">
                      {
                        "@context": "https://schema.org",
                        "@type": "Person",
                        "name": "陈安",
                        "gender": "男",
                        "birthDate": "2010-09-08",
                        "description": "2017 年在火车站附近走失。",
                        "image": "https://cdn.example.org/chen-an.jpg"
                      }
                    </script>
                  </head>
                </html>
                """));
        };
        var service = new CaseImportServiceImpl(
            new CaseImportParser(new ObjectMapper()), fetcher);
        var request = new CaseImportRequest();
        request.setSourceUrl("https://example.org/go/chen-an");

        var preview = service.preview(request).block();

        assertThat(requestedUrl).hasValue("https://example.org/go/chen-an");
        assertThat(preview).isNotNull();
        assertThat(preview.getCaseData().getPersonName()).isEqualTo("陈安");
        assertThat(preview.getCaseData().getGender()).isEqualTo("男");
        assertThat(preview.getCaseData().getBirthDate()).isEqualTo("2010-09-08");
        assertThat(preview.getCaseData().getDescription()).contains("火车站");
        assertThat(preview.getCaseData().getCoverImage())
            .isEqualTo("https://cdn.example.org/chen-an.jpg");
        assertThat(preview.getCaseData().getSourceName()).isEqualTo("公益寻亲网");
        assertThat(preview.getCaseData().getSourceUrl())
            .isEqualTo("https://www.example.org/missing/chen-an");
    }

    @Test
    void copiedPlainTextRecognizesOneFieldPerLine() {
        WebPageFetcher fetcher = sourceUrl -> Mono.error(new AssertionError("不应请求网络"));
        var service = new CaseImportServiceImpl(
            new CaseImportParser(new ObjectMapper()), fetcher);
        var request = new CaseImportRequest();
        request.setPageContent("""
            姓名：王小北
            性别：男
            走失时间：2020年10月2日
            走失地点：吉林省长春市火车站
            联系电话：138 0013 8000
            """);

        var preview = service.preview(request).block();

        assertThat(preview).isNotNull();
        assertThat(preview.getCaseData().getPersonName()).isEqualTo("王小北");
        assertThat(preview.getCaseData().getMissingDate()).isEqualTo("2020-10-02");
        assertThat(preview.getCaseData().getMissingLocation()).isEqualTo("吉林省长春市火车站");
        assertThat(preview.getCaseData().getContactPhone()).isEqualTo("138 0013 8000");
    }

    @Test
    void adjacentLabelAndValueElementsAreRecognized() {
        WebPageFetcher fetcher = sourceUrl -> Mono.error(new AssertionError("不应请求网络"));
        var service = new CaseImportServiceImpl(
            new CaseImportParser(new ObjectMapper()), fetcher);
        var request = new CaseImportRequest();
        request.setPageContent("""
            <section>
              <p><span>姓名：</span><strong>赵远</strong></p>
              <p><span>失踪地点：</span><strong>黑龙江省哈尔滨市</strong></p>
            </section>
            """);

        var preview = service.preview(request).block();

        assertThat(preview).isNotNull();
        assertThat(preview.getCaseData().getPersonName()).isEqualTo("赵远");
        assertThat(preview.getCaseData().getMissingLocation()).isEqualTo("黑龙江省哈尔滨市");
    }

    @Test
    void aiRequestIsDelegatedAfterRulePreviewAndKeepsReviewBoundary() {
        var aiService = mock(CaseImportAiService.class);
        when(aiService.preview(any(), any(), any(), any())).thenAnswer(invocation -> {
            CaseImportPreview preview = invocation.getArgument(3);
            preview.setAiGenerated(true);
            preview.getWarnings().add("AI 自动提取结果仅供参考");
            return Mono.just(preview);
        });
        var service = new CaseImportServiceImpl(new CaseImportParser(new ObjectMapper()),
            sourceUrl -> Mono.error(new AssertionError("粘贴正文时不应请求网络")),
            Optional.of(aiService));
        var request = new CaseImportRequest();
        request.setAi(true);
        request.setPageContent("姓名：林小满\n失踪地点：沈阳市和平区");

        var preview = service.preview(request).block();

        assertThat(preview).isNotNull();
        assertThat(preview.isAiGenerated()).isTrue();
        assertThat(preview.getCaseData().getPersonName()).isEqualTo("林小满");
        assertThat(preview.getWarnings()).contains("AI 自动提取结果仅供参考");
    }

    @Test
    void incompleteRulePreviewReportsConfiguredAiFallback() {
        var aiService = mock(CaseImportAiService.class);
        when(aiService.isAvailable()).thenReturn(Mono.just(true));
        var service = new CaseImportServiceImpl(new CaseImportParser(new ObjectMapper()),
            sourceUrl -> Mono.error(new AssertionError("粘贴正文时不应请求网络")),
            Optional.of(aiService));
        var request = new CaseImportRequest();
        request.setPageContent("<html><head><title>寻亲资料</title></head><body>页面内容</body></html>");

        var preview = service.preview(request).block();

        assertThat(preview).isNotNull();
        assertThat(preview.isAiRecommended()).isTrue();
        assertThat(preview.isAiAvailable()).isTrue();
        assertThat(preview.isAiGenerated()).isFalse();
    }

    @Test
    void batchPreviewReturnsIndependentSuccessAndFailureItems() {
        var aiService = new CaseImportAiService() {
            @Override
            public Mono<CaseImportPreview> preview(CaseImportRequest request, String content,
                                                    String sourceUrl, CaseImportPreview rulePreview) {
                if (content.contains("失败")) {
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "模型调用失败"));
                }
                return Mono.just(rulePreview);
            }

            @Override
            public Mono<Boolean> isAvailable() {
                return Mono.just(true);
            }

            @Override
            public Mono<CaseImportPreview> previewDocument(CaseImportRequest request,
                                                            byte[] content, String mediaType,
                                                            String filename) {
                return Mono.error(new UnsupportedOperationException());
            }
        };
        var service = new CaseImportServiceImpl(new CaseImportParser(new ObjectMapper()),
            sourceUrl -> Mono.error(new AssertionError("批量粘贴正文时不应请求网络")),
            Optional.of(aiService));
        var first = new CaseImportRequest();
        first.setPageContent("姓名：林小满");
        var second = new CaseImportRequest();
        second.setPageContent("失败");
        var batch = new CaseImportBatchRequest();
        batch.setItems(List.of(first, second));

        var result = service.previewBatch(batch).block();

        assertThat(result).isNotNull();
        assertThat(result.getSucceeded()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getItems().get(0).getPreview()).isNotNull();
        assertThat(result.getItems().get(1).getError()).isEqualTo("模型调用失败");
    }

    @Test
    void pageImagesBecomeAbsoluteCoverAndGalleryWithoutDecorativeImages() {
        WebPageFetcher fetcher = sourceUrl -> Mono.error(new AssertionError("不应请求网络"));
        var service = new CaseImportServiceImpl(
            new CaseImportParser(new ObjectMapper()), fetcher);
        var request = new CaseImportRequest();
        request.setSourceUrl("https://example.org/cases/zhao-yuan");
        request.setPageContent("""
            <html>
              <head><title>寻找赵远</title></head>
              <body>
                <img class="site-logo" src="/logo.png" width="120" height="40">
                <img src="/images/spacer.gif" width="4" height="4">
                <img alt="失踪人照片" data-src="/uploads/zhao-main.jpg">
                <img src="https://cdn.example.org/zhao-2.jpg" srcset="https://cdn.example.org/zhao-2-small.jpg 480w, https://cdn.example.org/zhao-2-large.jpg 1200w">
                <a class="gallery" href="/uploads/zhao-full.jpg"><img src="/uploads/zhao-thumb.jpg"></a>
              </body>
            </html>
            """);

        var preview = service.preview(request).block();

        assertThat(preview).isNotNull();
        assertThat(preview.getCaseData().getCoverImage())
            .isEqualTo("https://example.org/uploads/zhao-main.jpg");
        assertThat(preview.getCaseData().getGallery())
            .contains("https://cdn.example.org/zhao-2-large.jpg",
                "https://example.org/uploads/zhao-thumb.jpg",
                "https://example.org/uploads/zhao-full.jpg")
            .doesNotContain("https://example.org/logo.png",
                "https://example.org/images/spacer.gif",
                "https://cdn.example.org/zhao-2.jpg",
                preview.getCaseData().getCoverImage());
    }

    @Test
    void relativeMetadataCoverIsResolvedAndAiImportKeepsExtractedGallery() {
        var aiService = mock(CaseImportAiService.class);
        when(aiService.preview(any(), any(), any(), any())).thenAnswer(invocation -> {
            CaseImportPreview preview = invocation.getArgument(3);
            preview.setAiGenerated(true);
            return Mono.just(preview);
        });
        var service = new CaseImportServiceImpl(new CaseImportParser(new ObjectMapper()),
            sourceUrl -> Mono.error(new AssertionError("不应请求网络")),
            Optional.of(aiService));
        var request = new CaseImportRequest();
        request.setAi(true);
        request.setSourceUrl("https://news.example.org/missing/chen-an");
        request.setPageContent("""
            <html>
              <head><meta property="og:image" content="/media/cover.jpg"></head>
              <body>
                <p>姓名：陈安</p>
                <img src="/media/detail.jpg">
              </body>
            </html>
            """);

        var preview = service.preview(request).block();

        assertThat(preview).isNotNull();
        assertThat(preview.isAiGenerated()).isTrue();
        assertThat(preview.getCaseData().getCoverImage())
            .isEqualTo("https://news.example.org/media/cover.jpg");
        assertThat(preview.getCaseData().getGallery())
            .containsExactly("https://news.example.org/media/detail.jpg");
    }

    @Test
    void aiRequestExplainsMissingAiFoundationInsteadOfFallingBackSilently() {
        var service = new CaseImportServiceImpl(new CaseImportParser(new ObjectMapper()),
            sourceUrl -> Mono.error(new AssertionError("不应请求网络")));
        var request = new CaseImportRequest();
        request.setAi(true);
        request.setPageContent("姓名：林小满");

        assertThatThrownBy(() -> service.preview(request).block())
            .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
