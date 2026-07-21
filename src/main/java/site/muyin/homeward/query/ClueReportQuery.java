package site.muyin.homeward.query;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springdoc.core.fn.builders.operation.Builder;
import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.router.IListRequest;
import run.halo.app.extension.router.SortableRequest;

import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static run.halo.app.extension.index.query.Queries.contains;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.or;
import static run.halo.app.extension.router.QueryParamBuildUtil.sortParameter;
import static run.halo.app.extension.router.selector.SelectorUtil.labelAndFieldSelectorToListOptions;

public class ClueReportQuery extends SortableRequest {

    private final MultiValueMap<String, String> queryParams;

    public ClueReportQuery(ServerWebExchange exchange) {
        super(exchange);
        this.queryParams = new LinkedMultiValueMap<>(exchange.getRequest().getQueryParams());
    }

    @Nullable
    @Schema(description = "关键词")
    public String getKeyword() {
        return blankToNull(queryParams.getFirst("keyword"));
    }

    @Nullable
    @Schema(description = "案例 slug")
    public String getCaseSlug() {
        return blankToNull(queryParams.getFirst("caseSlug"));
    }

    @Nullable
    @Schema(description = "状态")
    public String getStatus() {
        return blankToNull(queryParams.getFirst("status"));
    }

    @Override
    public ListOptions toListOptions() {
        var listOptions = labelAndFieldSelectorToListOptions(getLabelSelector(), getFieldSelector());
        var query = ListOptions.builder(listOptions);
        if (getKeyword() != null) {
            query.andQuery(or(
                contains("spec.caseTitle", getKeyword()),
                contains("spec.submitterName", getKeyword()),
                contains("spec.contact", getKeyword()),
                contains("spec.content", getKeyword())
            ));
        }
        if (getCaseSlug() != null) {
            query.andQuery(equal("spec.caseSlug", getCaseSlug()));
        }
        if (getStatus() != null) {
            query.andQuery(equal("spec.status", getStatus()));
        }
        return query.build();
    }

    @Override
    public PageRequest toPageRequest() {
        var sort = getSort();
        if (sort.isUnsorted()) {
            sort = Sort.by("spec.submittedAt").descending();
        }
        return PageRequestImpl.of(getPage(), getSize(), sort);
    }

    public static void buildParameters(Builder builder) {
        IListRequest.buildParameters(builder);
        builder.parameter(sortParameter())
            .parameter(parameterBuilder().in(ParameterIn.QUERY).name("keyword")
                .description("关键词").implementation(String.class).required(false))
            .parameter(parameterBuilder().in(ParameterIn.QUERY).name("caseSlug")
                .description("案例 slug").implementation(String.class).required(false))
            .parameter(parameterBuilder().in(ParameterIn.QUERY).name("status")
                .description("状态").implementation(String.class).required(false));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
