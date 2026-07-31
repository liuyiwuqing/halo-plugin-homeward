package site.muyin.homeward.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Resolves the same-origin JSON data used by the Ministry of Civil Affairs seeking-detail page.
 *
 * <p>The resolver is deliberately limited to the official detail path. It never executes page
 * scripts or follows a URL read from page content.</p>
 */
final class McaMissingPersonPageResolver {

    private static final String MCA_HOST = "www.mca.gov.cn";

    private static final String DETAIL_PATH = "/lljz/indexdetail.html";

    private static final String DETAIL_API_PATH =
        "/lljz/jmgc-relief-seeking/familySeekers/querySeekingInfoById";

    private static final Pattern DETAIL_ID_PATTERN = Pattern.compile("[A-Za-z0-9-]{8,128}");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private McaMissingPersonPageResolver() {
    }

    static Optional<URI> detailApiUri(URI source) {
        if (source == null || !MCA_HOST.equalsIgnoreCase(source.getHost())
            || !DETAIL_PATH.equals(source.getPath())) {
            return Optional.empty();
        }
        String id = queryParameter(source.getRawQuery(), "id");
        if (id == null || !DETAIL_ID_PATTERN.matcher(id).matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new URI(source.getScheme().toLowerCase(Locale.ROOT), null,
                source.getHost(), source.getPort(), DETAIL_API_PATH, "id=" + id, null));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    static String enrich(URI source, String pageContent, String apiResponse) {
        if (detailApiUri(source).isEmpty()) {
            return pageContent;
        }
        try {
            JsonNode detail = OBJECT_MAPPER.readTree(apiResponse).path("data").path("data");
            if (!detail.isObject()) {
                return pageContent;
            }
            Document document = Jsoup.parse(pageContent, source.toString());
            Element section = document.body().appendElement("section")
                .attr("data-homeward-source", "mca-dynamic-detail");
            Element table = section.appendElement("table");
            String name = text(detail, "name");
            appendRow(table, "标题", hasText(name) ? "民政部寻亲公告：" + name : null);
            appendRow(table, "姓名", name);
            appendRow(table, "性别", text(detail, "sex"));
            appendRow(table, "当前年龄", text(detail, "age"));
            appendRow(table, "体貌特征", text(detail, "posture"));
            appendRow(table, "详细描述", description(detail));
            appendRow(table, "联系方式", text(detail, "contactDetails"));
            appendRow(table, "来源名称", "民政部流浪乞讨人员寻亲网");
            String imageUrl = imageUrl(source, text(detail, "picUrl"));
            if (hasText(imageUrl)) {
                document.head().appendElement("meta")
                    .attr("property", "og:image")
                    .attr("content", imageUrl);
            }
            return document.outerHtml();
        } catch (Exception ignored) {
            return pageContent;
        }
    }

    private static String description(JsonNode detail) {
        var values = new ArrayList<String>();
        appendDescription(values, "发现/解救时间", text(detail, "findRescueTime"));
        appendDescription(values, "发现/解救地点", text(detail, "findRescuePlace"));
        appendDescription(values, "发现时衣着特征", text(detail, "findRescueFeature"));
        appendDescription(values, "救助单位", text(detail, "rescueUnit"));
        appendDescription(values, "救助编号", text(detail, "childNumber"));
        appendDescription(values, "发布日期", text(detail, "releaseTime"));
        return values.isEmpty() ? null : String.join("；", values);
    }

    private static void appendDescription(ArrayList<String> values, String label, String value) {
        if (hasText(value)) {
            values.add(label + "：" + value);
        }
    }

    private static void appendRow(Element table, String label, String value) {
        if (!hasText(value)) {
            return;
        }
        Element row = table.appendElement("tr");
        row.appendElement("th").text(label);
        row.appendElement("td").text(value);
    }

    private static String imageUrl(URI source, String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return source.resolve(value).toString();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isContainerNode()) {
            return null;
        }
        String text = value.asText();
        return hasText(text) ? text.trim() : null;
    }

    private static String queryParameter(String rawQuery, String name) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }
        for (String item : rawQuery.split("&")) {
            int separator = item.indexOf('=');
            String key = separator < 0 ? item : item.substring(0, separator);
            if (!name.equals(URLDecoder.decode(key, StandardCharsets.UTF_8))) {
                continue;
            }
            String value = separator < 0 ? "" : item.substring(separator + 1);
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
