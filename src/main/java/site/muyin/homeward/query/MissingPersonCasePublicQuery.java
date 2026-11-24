package site.muyin.homeward.query;

import static run.halo.app.extension.index.query.Queries.contains;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;
import static run.halo.app.extension.index.query.Queries.or;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.PageRequestImpl;
import site.muyin.homeward.enums.CaseStatus;

/** 公开查询只接收搜索与分页参数，不接受 Console 的字段选择器和排序。 */
public record MissingPersonCasePublicQuery(String keyword, int page, int size) {

    public MissingPersonCasePublicQuery {
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (keyword != null && keyword.length() > 120) {
            throw badRequest("搜索关键词不能超过 120 个字符");
        }
        if (page < 1 || size < 1) {
            throw badRequest("page 和 size 必须为正整数");
        }
        size = Math.min(size, 50);
    }

    public static MissingPersonCasePublicQuery from(MultiValueMap<String, String> params) {
        return new MissingPersonCasePublicQuery(params.getFirst("keyword"),
            positiveInteger(params.getFirst("page"), 1),
            positiveInteger(params.getFirst("size"), 20));
    }

    public static ListOptions publishedOptions() {
        return ListOptions.builder()
            .andQuery(equal("spec.status", CaseStatus.PUBLISHED.name()))
            .andQuery(isNull("metadata.deletionTimestamp"))
            .build();
    }

    public ListOptions toListOptions() {
        var options = ListOptions.builder(publishedOptions());
        if (keyword != null) {
            options.andQuery(or(contains("spec.personName", keyword),
                contains("spec.title", keyword), contains("spec.missingLocation", keyword)));
        }
        return options.build();
    }

    public PageRequest toPageRequest() {
        return PageRequestImpl.of(page, size, Sort.by("spec.priority").descending()
            .and(Sort.by("spec.publishedAt").descending())
            .and(Sort.by("metadata.name")));
    }

    private static int positiveInteger(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw badRequest("page 和 size 必须为正整数");
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
