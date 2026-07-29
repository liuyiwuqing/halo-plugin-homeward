package site.muyin.homeward.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HomewardExtensionResourcesTest {

    @Test
    void anonymousRoleCoversPublicSubmitPathAsNonResourceUrl() throws Exception {
        try (var input = getClass().getResourceAsStream("/extensions/roleTemplates.yaml")) {
            assertThat(input).isNotNull();
            String yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(yaml)
                .contains("rbac.authorization.halo.run/aggregate-to-anonymous")
                .contains("/apis/public.homeward.muyin.site/v1alpha1/cases/*/clues");
        }
    }

    @Test
    void registersNotFoundFilterAsAdditionalWebFilter() throws Exception {
        try (var input = getClass().getResourceAsStream("/extensions/extension-definitions.yaml")) {
            assertThat(input).isNotNull();
            String yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(yaml)
                .contains("extensionPointName: additional-webfilter")
                .contains("className: site.muyin.homeward.filter.HomewardNotFoundWebFilter");
        }
    }

    @Test
    void notificationRecipientsUseHaloUserSelector() throws Exception {
        try (var input = getClass().getResourceAsStream("/extensions/settings.yaml")) {
            assertThat(input).isNotNull();
            String yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(yaml)
                .contains("$formkit: userSelect")
                .contains("name: recipients")
                .doesNotContain("addLabel: 添加用户");
        }
    }
}
