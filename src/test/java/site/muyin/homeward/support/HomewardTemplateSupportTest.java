package site.muyin.homeward.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.ITemplateContext;

class HomewardTemplateSupportTest {

    @Test
    void detectsNotFoundContextFromErrorStatus() {
        ITemplateContext context = mock(ITemplateContext.class);
        when(context.getVariable("error")).thenReturn(Map.of("status", 404));

        assertThat(HomewardTemplateSupport.isNotFoundContext(context)).isTrue();
    }

    @Test
    void skipsConsoleAndApiPaths() {
        assertThat(HomewardTemplateSupport.shouldSkipPath("/console/homeward")).isTrue();
        assertThat(HomewardTemplateSupport.shouldSkipPath("/apis/public.homeward.muyin.site/v1alpha1/widget"))
            .isTrue();
        assertThat(HomewardTemplateSupport.shouldSkipPath("/archives/missing-page")).isFalse();
    }
}
