package site.muyin.homeward.router;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import site.muyin.homeward.model.MissingPersonCasePublicVO;

class HomewardTemplateRenderingTest {

    @Test
    void sharedCaseHtmlContainsFullPublicFactsAndEscapesImportedText() throws Exception {
        var item = new MissingPersonCasePublicVO();
        item.setSlug("sample-person");
        item.setTitle("测试档案 <script>alert(1)</script>");
        item.setPersonName("测试人员");
        item.setMissingLocation("测试城市");
        item.setDescription("第一行完整说明\n第二行补充说明");
        item.setPhysicalFeatures("测试外貌特征");
        item.setSourceName("测试资料来源");
        item.setPublishedAt(Instant.parse("2026-10-11T03:00:00Z"));
        var context = new Context();
        context.setVariables(Map.of(
            "caseData", item,
            "caseUrl", "https://example.test/homeward/cases/sample-person",
            "safeCoverImage", "/photos/sample.jpg",
            "shareImageUrl", "https://example.test/photos/sample.jpg",
            "safeGallery", List.of("/photos/second.jpg"),
            "safeSourceUrl", "https://source.test/sample",
            "publishedDateText", "2026-10-11 03:00 UTC",
            "updatedDateText", ""
        ));
        var html = engine().process("homeward-case", context);
        var page = Jsoup.parse(html);

        assertThat(page.selectFirst("h1").text()).isEqualTo(item.getTitle());
        assertThat(page.select("script:not([src])")).isEmpty();
        assertThat(page.select("link[rel=canonical]").attr("href"))
            .isEqualTo("https://example.test/homeward/cases/sample-person");
        assertThat(page.select("meta[property=og:image]").attr("content"))
            .isEqualTo("https://example.test/photos/sample.jpg");
        assertThat(page.text()).contains("第一行完整说明", "第二行补充说明", "测试外貌特征", "测试资料来源");
        assertThat(page.select("a[href='https://source.test/sample']")).hasSize(1);
        assertThat(page.select("a[href='/photos/second.jpg'] img")).hasSize(1);
        assertThat(page.select("[th\\:text], [th\\:href]")).isEmpty();

        // Rendered fixtures are build outputs used for isolated browser smoke checks.
        var target = Path.of("build", "browser-fixtures");
        Files.createDirectories(target);
        Files.writeString(target.resolve("case.html"), html, StandardCharsets.UTF_8);
    }

    @Test
    void normalAndNotFoundTemplatesSelectTheirOwnDisplaySurface() throws Exception {
        var target = Path.of("build", "browser-fixtures");
        Files.createDirectories(target);
        for (boolean notFound : List.of(false, true)) {
            var context = new Context();
            context.setVariable("homeward404", notFound);
            var html = engine().process("homeward", context);
            var page = Jsoup.parse(html);
            assertThat(page.select(".homeward-not-found-notice").isEmpty()).isEqualTo(!notFound);
            assertThat(html).contains("window.__HOMEWARD_404__ = " + notFound)
                .contains("window.__HOMEWARD_PAGE__ = !window.__HOMEWARD_404__");
            Files.writeString(target.resolve(notFound ? "404.html" : "list.html"), html, StandardCharsets.UTF_8);
        }
    }

    private SpringTemplateEngine engine() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
