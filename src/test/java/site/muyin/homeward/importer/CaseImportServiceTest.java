package site.muyin.homeward.importer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import site.muyin.homeward.enums.CaseStatus;
import site.muyin.homeward.model.CaseImportRequest;

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
}
