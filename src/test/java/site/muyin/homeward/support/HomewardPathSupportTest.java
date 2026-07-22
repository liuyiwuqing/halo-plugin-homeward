package site.muyin.homeward.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HomewardPathSupportTest {

    @Test
    void skipsConsoleApiAndPluginPaths() {
        assertThat(HomewardPathSupport.shouldSkip("/console/homeward")).isTrue();
        assertThat(HomewardPathSupport.shouldSkip("/apis/public.homeward.muyin.site/v1alpha1/widget"))
            .isTrue();
        assertThat(HomewardPathSupport.shouldSkip("/plugins/homeward/assets/logo.svg")).isTrue();
        assertThat(HomewardPathSupport.shouldSkip("/archives/missing-page")).isFalse();
    }
}
