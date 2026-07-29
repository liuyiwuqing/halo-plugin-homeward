package site.muyin.homeward.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import site.muyin.homeward.enums.CaseStatus;
import site.muyin.homeward.model.CaseImportPreview;
import site.muyin.homeward.model.MissingPersonCaseVO;

@Component
public class CaseImportParser {

    private static final Pattern DATE_PATTERN = Pattern.compile(
        "(?<!\\d)(\\d{4})[年./-](\\d{1,2})[月./-](\\d{1,2})日?(?!\\d)");

    private static final Pattern HTML_PATTERN = Pattern.compile("<[/!a-zA-Z][^>]*>");

    private static final Map<String, String> FIELD_ALIASES = fieldAliases();

    private final ObjectMapper objectMapper;

    public CaseImportParser() {
        this(new ObjectMapper());
    }

    CaseImportParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CaseImportPreview parse(String content, String sourceUrl) {
        boolean html = looksLikeHtml(content);
        Document document = html
            ? Jsoup.parse(content, sourceUrl == null ? "" : sourceUrl)
            : Jsoup.parse("<body><pre></pre></body>");
        if (!html) {
            document.selectFirst("pre").text(content);
        }

        var values = new LinkedHashMap<String, String>();
        extractTableValues(document, values);
        extractDefinitionValues(document, values);
        extractSiblingValues(document, values);
        extractLineValues(document, values);
        extractStructuredData(document, values);
        extractMetadata(document, values);

        var caseData = new MissingPersonCaseVO();
        caseData.setPersonName(value(values, "personName"));
        caseData.setGender(normalizeGender(value(values, "gender")));
        caseData.setBirthDate(normalizeDate(value(values, "birthDate")));
        caseData.setMissingDate(normalizeDate(value(values, "missingDate")));
        caseData.setMissingAge(normalizeAge(value(values, "missingAge")));
        caseData.setCurrentAge(normalizeAge(value(values, "currentAge")));
        caseData.setMissingLocation(value(values, "missingLocation"));
        caseData.setHometown(value(values, "hometown"));
        caseData.setPhysicalFeatures(value(values, "physicalFeatures"));
        caseData.setDescription(value(values, "description"));
        caseData.setContactName(value(values, "contactName"));
        caseData.setContactPhone(value(values, "contactPhone"));
        caseData.setTitle(firstText(value(values, "title"), cleanTitle(document.title())));
        caseData.setCoverImage(value(values, "coverImage"));
        caseData.setSourceName(firstText(value(values, "sourceName"), sourceName(document, sourceUrl)));
        caseData.setSourceUrl(sourceUrl);
        caseData.setStatus(CaseStatus.DRAFT);
        caseData.setPriority(0);

        var preview = new CaseImportPreview();
        preview.setCaseData(caseData);
        preview.setExtractedFieldCount(countExtractedFields(caseData));
        if (!hasText(caseData.getPersonName())) {
            preview.getWarnings().add("未识别到姓名，请在保存前补充");
        }
        if (preview.getExtractedFieldCount() < 3) {
            preview.getWarnings().add("当前网页可识别字段较少，请粘贴正文或手动补充资料");
        }
        return preview;
    }

    private void extractStructuredData(Document document, Map<String, String> values) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            try {
                visitStructuredNode(objectMapper.readTree(script.data()), values);
            } catch (Exception ignored) {
                // Malformed JSON-LD must not make the whole import unusable.
            }
        }
    }

    private void visitStructuredNode(JsonNode node, Map<String, String> values) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> visitStructuredNode(item, values));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        if (isSchemaType(node.get("@type"), "Person")) {
            putFieldValue(values, "personName", text(node, "name"));
            putFieldValue(values, "gender", schemaGender(text(node, "gender")));
            putFieldValue(values, "birthDate", text(node, "birthDate"));
            putFieldValue(values, "description", text(node, "description"));
            putFieldValue(values, "contactPhone", text(node, "telephone"));
            putFieldValue(values, "coverImage", imageUrl(node.get("image")));
        }
        if (isSchemaType(node.get("@type"), "Article")
            || isSchemaType(node.get("@type"), "NewsArticle")) {
            putFieldValue(values, "title", firstText(text(node, "headline"), text(node, "name")));
            putFieldValue(values, "description", text(node, "description"));
            putFieldValue(values, "coverImage", imageUrl(node.get("image")));
        }
        visitStructuredNode(node.get("@graph"), values);
    }

    private static void extractMetadata(Document document, Map<String, String> values) {
        putFieldValue(values, "title", metaContent(document, "meta[property=og:title]"));
        putFieldValue(values, "description",
            firstText(metaContent(document, "meta[property=og:description]"),
                metaContent(document, "meta[name=description]")));
        putFieldValue(values, "coverImage", metaContent(document, "meta[property=og:image]"));
        putFieldValue(values, "sourceName", metaContent(document, "meta[property=og:site_name]"));
    }

    private static void extractTableValues(Document document, Map<String, String> values) {
        for (Element row : document.select("tr")) {
            var cells = row.select("th, td");
            if (cells.size() >= 2) {
                putValue(values, cells.get(0).text(), cells.get(1).text());
            }
        }
    }

    private static void extractDefinitionValues(Document document, Map<String, String> values) {
        for (Element term : document.select("dt")) {
            Element description = term.nextElementSibling();
            if (description != null && "dd".equalsIgnoreCase(description.tagName())) {
                putValue(values, term.text(), description.text());
            }
        }
    }

    private static void extractSiblingValues(Document document, Map<String, String> values) {
        for (Element element : document.getAllElements()) {
            String label = element.ownText();
            if (!hasText(label) || label.length() > 20
                || !FIELD_ALIASES.containsKey(normalizeLabel(label))) {
                continue;
            }
            Element valueElement = element.nextElementSibling();
            if (valueElement != null) {
                putValue(values, label, valueElement.text());
            }
        }
    }

    private static void extractLineValues(Document document, Map<String, String> values) {
        for (Element element : document.select("p, li, div, span, pre")) {
            String ownText = element.ownText();
            if (!hasText(ownText) || ownText.length() > 2_000) {
                continue;
            }
            for (String line : ownText.split("[\\r\\n]+")) {
                int separator = separatorIndex(line);
                if (separator > 0 && separator <= 20) {
                    putValue(values, line.substring(0, separator), line.substring(separator + 1));
                }
            }
        }
    }

    private static void putValue(Map<String, String> values, String label, String rawValue) {
        String field = FIELD_ALIASES.get(normalizeLabel(label));
        String value = cleanValue(rawValue);
        if (field != null && hasText(value)) {
            values.putIfAbsent(field, value);
        }
    }

    private static void putFieldValue(Map<String, String> values, String field, String rawValue) {
        String value = cleanValue(rawValue);
        if (hasText(value)) {
            values.putIfAbsent(field, value);
        }
    }

    private static String value(Map<String, String> values, String field) {
        return values.get(field);
    }

    private static Map<String, String> fieldAliases() {
        var aliases = new LinkedHashMap<String, String>();
        aliases(aliases, "personName", "姓名", "失踪人姓名", "走失者姓名", "寻亲人姓名", "宝贝姓名");
        aliases(aliases, "gender", "性别");
        aliases(aliases, "birthDate", "出生日期", "出生时间", "生日");
        aliases(aliases, "missingDate", "失踪日期", "走失日期", "失踪时间", "走失时间");
        aliases(aliases, "missingAge", "失踪年龄", "走失年龄", "失踪时年龄");
        aliases(aliases, "currentAge", "当前年龄", "现在年龄");
        aliases(aliases, "missingLocation", "失踪地点", "走失地点", "失踪地址", "走失地址");
        aliases(aliases, "hometown", "籍贯", "家乡", "户籍地");
        aliases(aliases, "physicalFeatures", "体貌特征", "外貌特征", "身体特征", "特征描述");
        aliases(aliases, "description", "详细描述", "寻亲经过", "走失经过", "案例详情", "其他资料");
        aliases(aliases, "contactName", "联系人", "联系人姓名");
        aliases(aliases, "contactPhone", "联系电话", "联系方式", "联系人电话");
        aliases(aliases, "title", "案例标题", "标题");
        aliases(aliases, "sourceName", "信息来源", "来源名称", "来源");
        return Map.copyOf(aliases);
    }

    private static void aliases(Map<String, String> aliases, String field, String... labels) {
        for (String label : labels) {
            aliases.put(normalizeLabel(label), field);
        }
    }

    private static String normalizeLabel(String label) {
        if (label == null) {
            return "";
        }
        return label.replaceAll("[\\s:：()（）]", "").toLowerCase(Locale.ROOT);
    }

    private static String cleanValue(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static int separatorIndex(String line) {
        int chinese = line.indexOf('：');
        int ascii = line.indexOf(':');
        if (chinese < 0) {
            return ascii;
        }
        if (ascii < 0) {
            return chinese;
        }
        return Math.min(chinese, ascii);
    }

    private static String normalizeGender(String value) {
        if (!hasText(value)) {
            return null;
        }
        String cleaned = value.trim();
        if (cleaned.startsWith("男")) {
            return "男";
        }
        if (cleaned.startsWith("女")) {
            return "女";
        }
        return cleaned;
    }

    private static String schemaGender(String value) {
        if (!hasText(value)) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "male", "m" -> "男";
            case "female", "f" -> "女";
            default -> value;
        };
    }

    private static String normalizeDate(String value) {
        if (!hasText(value)) {
            return null;
        }
        var matcher = DATE_PATTERN.matcher(value);
        if (!matcher.find()) {
            return value.trim();
        }
        try {
            return LocalDate.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))).toString();
        } catch (DateTimeException ignored) {
            return value.trim();
        }
    }

    private static Integer normalizeAge(String value) {
        if (!hasText(value)) {
            return null;
        }
        var matcher = Pattern.compile("\\d{1,3}").matcher(value);
        if (!matcher.find()) {
            return null;
        }
        int age = Integer.parseInt(matcher.group());
        return age <= 150 ? age : null;
    }

    private static String sourceName(Document document, String sourceUrl) {
        String siteName = metaContent(document, "meta[property=og:site_name]");
        if (hasText(siteName)) {
            return siteName;
        }
        if (hasText(sourceUrl)) {
            try {
                return URI.create(sourceUrl).getHost();
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String metaContent(Document document, String selector) {
        Element meta = document.selectFirst(selector);
        return meta == null ? null : cleanValue(meta.attr("content"));
    }

    private static String cleanTitle(String title) {
        if (!hasText(title)) {
            return null;
        }
        return title.split("\\s+[-_|—]\\s+", 2)[0].trim();
    }

    private static boolean isSchemaType(JsonNode typeNode, String expected) {
        if (typeNode == null) {
            return false;
        }
        if (typeNode.isArray()) {
            for (JsonNode item : typeNode) {
                if (isSchemaType(item, expected)) {
                    return true;
                }
            }
            return false;
        }
        String type = typeNode.asText("");
        int separator = Math.max(type.lastIndexOf('/'), type.lastIndexOf('#'));
        String shortType = separator >= 0 ? type.substring(separator + 1) : type;
        return expected.equalsIgnoreCase(shortType);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isContainerNode() ? null : value.asText(null);
    }

    private static String imageUrl(JsonNode image) {
        if (image == null || image.isNull()) {
            return null;
        }
        if (image.isTextual()) {
            return image.asText();
        }
        if (image.isArray()) {
            for (JsonNode item : image) {
                String url = imageUrl(item);
                if (hasText(url)) {
                    return url;
                }
            }
            return null;
        }
        return firstText(text(image, "contentUrl"), text(image, "url"));
    }

    private static int countExtractedFields(MissingPersonCaseVO value) {
        int count = (int) Stream.of(value.getPersonName(), value.getGender(), value.getBirthDate(),
                value.getMissingDate(), value.getMissingLocation(), value.getHometown(),
                value.getPhysicalFeatures(), value.getDescription(), value.getContactName(),
                value.getContactPhone())
            .filter(CaseImportParser::hasText)
            .count();
        if (value.getMissingAge() != null) {
            count++;
        }
        if (value.getCurrentAge() != null) {
            count++;
        }
        return count;
    }

    private static boolean looksLikeHtml(String content) {
        return content != null && HTML_PATTERN.matcher(content).find();
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
