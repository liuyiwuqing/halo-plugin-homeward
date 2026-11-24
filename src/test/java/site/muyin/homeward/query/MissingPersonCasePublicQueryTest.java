package site.muyin.homeward.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.server.ResponseStatusException;

class MissingPersonCasePublicQueryTest {

    @Test
    void publicSearchIgnoresSelectorsAndOnlyQueriesPublishedUndeletedCases() {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("keyword", "  南京  ");
        params.add("page", "2");
        params.add("size", "1000");
        params.add("fieldSelector", "spec.status=DRAFT");
        params.add("labelSelector", "private=true");
        params.add("status", "ARCHIVED");
        params.add("sort", "spec.contactPhone,asc");

        var query = MissingPersonCasePublicQuery.from(params);

        assertThat(query.keyword()).isEqualTo("南京");
        assertThat(query.toPageRequest().getPageNumber()).isEqualTo(2);
        assertThat(query.toPageRequest().getPageSize()).isEqualTo(50);
        assertThat(query.toPageRequest().getSort().toString()).doesNotContain("contactPhone");
        assertThat(query.toListOptions().toString())
            .contains("spec.status = PUBLISHED", "metadata.deletionTimestamp IS NULL",
                "spec.personName", "spec.title", "spec.missingLocation", "南京")
            .doesNotContain("DRAFT", "ARCHIVED", "private");
    }

    @Test
    void defaultPaginationDoesNotAllowUnboundedLists() {
        var query = MissingPersonCasePublicQuery.from(new LinkedMultiValueMap<>());
        assertThat(query.page()).isEqualTo(1);
        assertThat(query.size()).isEqualTo(20);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "", "1.5", "2147483648"})
    void invalidPageAndSizeAreBadRequests(String value) {
        for (String parameter : new String[]{"page", "size"}) {
            var params = new LinkedMultiValueMap<String, String>();
            params.add(parameter, value);
            assertThatThrownBy(() -> MissingPersonCasePublicQuery.from(params))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                    error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    @Test
    void excessiveKeywordIsRejected() {
        assertThatThrownBy(() -> new MissingPersonCasePublicQuery("字".repeat(121), 1, 20))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
