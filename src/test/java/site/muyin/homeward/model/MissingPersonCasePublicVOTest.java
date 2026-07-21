package site.muyin.homeward.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import site.muyin.homeward.scheme.MissingPersonCase;

class MissingPersonCasePublicVOTest {

    @Test
    void publicViewDoesNotExposePrivateContactPhone() {
        var item = new MissingPersonCase();
        item.getSpec().setSlug("case-a");
        item.getSpec().setPersonName("小安");
        item.getSpec().setContactName("公益联络员");
        item.getSpec().setContactPhone("13800000000");

        var vo = MissingPersonCasePublicVO.from(item);
        var publicFieldNames = Arrays.stream(MissingPersonCasePublicVO.class.getDeclaredFields())
            .map(java.lang.reflect.Field::getName)
            .toList();

        assertThat(vo.getContactName()).isEqualTo("公益联络员");
        assertThat(publicFieldNames).doesNotContain("contactPhone", "ipHash", "processNote");
    }
}
