package site.muyin.homeward.importer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import org.junit.jupiter.api.Test;

class McaMissingPersonPageResolverTest {

    @Test
    void officialDetailAddressResolvesToTheSameOriginDataApi() {
        URI source = URI.create("https://www.mca.gov.cn/lljz/indexdetail.html?"
            + "id=7de67c2bf48f4939a3e55b0496405e54&type=2");

        var detailApi = McaMissingPersonPageResolver.detailApiUri(source);

        assertThat(detailApi).hasValue(URI.create("https://www.mca.gov.cn/lljz/"
            + "jmgc-relief-seeking/familySeekers/querySeekingInfoById?"
            + "id=7de67c2bf48f4939a3e55b0496405e54"));
    }

    @Test
    void dynamicDetailJsonIsAddedAsReviewableSourceFields() {
        URI source = URI.create("https://www.mca.gov.cn/lljz/indexdetail.html?"
            + "id=7de67c2bf48f4939a3e55b0496405e54&type=2");
        String content = McaMissingPersonPageResolver.enrich(source,
            "<html><head><title>寻亲首页-流浪</title></head><body></body></html>", """
                {
                  "code": 200,
                  "data": {
                    "data": {
                      "name": "赵罗博",
                      "sex": "男",
                      "age": 60,
                      "posture": "肥胖",
                      "findRescueFeature": "深色上衣，深色下衣",
                      "findRescuePlace": "山东省菏泽市鄄城县古泉街道",
                      "findRescueTime": "2026-07-03",
                      "contactDetails": "05307151988",
                      "rescueUnit": "鄄城县社会救助服务中心",
                      "childNumber": "37172620260703001",
                      "picUrl": "useruploadfiles/278887/avatar.png"
                    }
                  }
                }
                """);

        var preview = new CaseImportParser(new ObjectMapper()).parse(content, source.toString());

        assertThat(preview.getCaseData().getPersonName()).isEqualTo("赵罗博");
        assertThat(preview.getCaseData().getGender()).isEqualTo("男");
        assertThat(preview.getCaseData().getCurrentAge()).isEqualTo(60);
        assertThat(preview.getCaseData().getPhysicalFeatures()).isEqualTo("肥胖");
        assertThat(preview.getCaseData().getContactPhone()).isEqualTo("05307151988");
        assertThat(preview.getCaseData().getDescription())
            .contains("发现/解救地点：山东省菏泽市鄄城县古泉街道")
            .contains("救助单位：鄄城县社会救助服务中心");
        assertThat(preview.getCaseData().getCoverImage())
            .isEqualTo("https://www.mca.gov.cn/lljz/useruploadfiles/278887/avatar.png");
    }
}
