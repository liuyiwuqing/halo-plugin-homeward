package site.muyin.homeward.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ClueSubmitResultPublicContractTest {

    @Test
    void publicSubmitResultDoesNotExposeInternalClueName() {
        var publicFieldNames = Arrays.stream(ClueSubmitResult.class.getDeclaredFields())
            .map(java.lang.reflect.Field::getName)
            .toList();

        assertThat(publicFieldNames).contains("status", "message");
        assertThat(publicFieldNames).doesNotContain("clueName", "ipHash", "processNote", "handler");
    }
}
