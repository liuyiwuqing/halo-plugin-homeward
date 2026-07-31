package site.muyin.homeward.importer.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.chat.StreamTextResult;
import run.halo.aifoundation.schema.OutputType;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;
import site.muyin.homeward.importer.CaseImportParser;
import site.muyin.homeward.model.CaseImportRequest;
import site.muyin.homeward.setting.HomewardAiSetting;

class CaseImportAiServiceImplTest {

    @Test
    void serviceCanBeCreatedWithoutSpringBootObjectMapperBean() {
        try (var context = new AnnotationConfigApplicationContext(HaloLikeContext.class)) {
            assertThat(context.getBean(CaseImportAiServiceImpl.class)).isNotNull();
        }
    }

    @Test
    void plainJsonTextOutputIsConvertedIntoReviewableCaseData() {
        var languageModel = mock(LanguageModel.class);
        when(languageModel.streamText(any(GenerateTextRequest.class))).thenReturn(streamed(
            GenerateTextResult.builder()
                .text("""
                    ```json
                    {"personName":"陈安","missingLocation":"沈阳市和平区",
                    "description":"2017 年在火车站附近走失。"}
                    ```
                    """)
                .build()));
        var service = service(languageModel);
        var request = new CaseImportRequest();
        request.setAi(true);

        var preview = service.preview(request,
            "2017 年在火车站附近走失，家人寻找陈安。", null,
            new CaseImportParser().parse("2017 年在火车站附近走失，家人寻找陈安。", null))
            .block();

        assertThat(preview).isNotNull();
        assertThat(preview.isAiGenerated()).isTrue();
        assertThat(preview.getParser()).isEqualTo("AI+RULE");
        assertThat(preview.getCaseData().getPersonName()).isEqualTo("陈安");
        assertThat(preview.getCaseData().getMissingLocation()).isEqualTo("沈阳市和平区");
        assertThat(preview.getWarnings()).anyMatch(item -> item.contains("逐项核对"));
        var generationRequest = ArgumentCaptor.forClass(GenerateTextRequest.class);
        verify(languageModel).streamText(generationRequest.capture());
        assertThat(generationRequest.getValue().getOutput().getType()).isEqualTo(OutputType.TEXT);
    }

    @Test
    void binaryDocumentUsesFileMessageInput() {
        var languageModel = mock(LanguageModel.class);
        when(languageModel.streamText(any(GenerateTextRequest.class))).thenReturn(streamed(
            GenerateTextResult.builder()
                .text("{\"personName\":\"王小北\",\"gender\":\"男\"}")
                .build()));
        var service = service(languageModel);
        var request = new CaseImportRequest();
        request.setAi(true);

        var preview = service.previewDocument(request, new byte[] {1, 2, 3}, "application/pdf",
            "case.pdf").block();

        assertThat(preview).isNotNull();
        assertThat(preview.getCaseData().getPersonName()).isEqualTo("王小北");
        assertThat(preview.getCaseData().getSourceName()).isEqualTo("case.pdf");
        assertThat(preview.getCaseData().getGender()).isEqualTo("男");
        var generationRequest = ArgumentCaptor.forClass(GenerateTextRequest.class);
        verify(languageModel).streamText(generationRequest.capture());
        assertThat(generationRequest.getValue().getOutput().getType()).isEqualTo(OutputType.TEXT);
    }

    private static CaseImportAiServiceImpl service(LanguageModel languageModel) {
        var extensionGetter = mock(ExtensionGetter.class);
        var aiModelService = mock(AiModelService.class);
        var settingFetcher = mock(ReactiveSettingFetcher.class);
        when(settingFetcher.fetch(eq(HomewardAiSetting.GROUP), eq(HomewardAiSetting.class)))
            .thenReturn(Mono.just(HomewardAiSetting.defaults()));
        when(extensionGetter.getEnabledExtension(AiModelService.class))
            .thenReturn(Mono.just(aiModelService));
        when(aiModelService.languageModel(nullable(String.class)))
            .thenReturn(Mono.just(languageModel));
        return new CaseImportAiServiceImpl(extensionGetter, settingFetcher, new CaseImportParser());
    }

    private static StreamTextResult streamed(GenerateTextResult result) {
        return new StreamTextResult(Flux.empty(), Flux.empty(), Flux.empty(), Flux.empty(),
            Mono.justOrEmpty(result.getOutput()), Mono.just(result));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CaseImportAiServiceImpl.class)
    static class HaloLikeContext {

        @Bean
        ExtensionGetter extensionGetter() {
            return mock(ExtensionGetter.class);
        }

        @Bean
        ReactiveSettingFetcher settingFetcher() {
            return mock(ReactiveSettingFetcher.class);
        }

        @Bean
        CaseImportParser caseImportParser() {
            return new CaseImportParser();
        }
    }
}
