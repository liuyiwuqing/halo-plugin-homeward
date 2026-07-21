package site.muyin.homeward.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.IModel;
import org.thymeleaf.model.IModelFactory;
import org.thymeleaf.model.IText;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.PluginContext;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.homeward.setting.HomewardDisplaySetting;
import site.muyin.homeward.setting.HomewardGeneralSetting;

class HomewardHeadProcessorTest {

    @Test
    void injectsAssetsForEnabled404Context() {
        var pluginContext = org.mockito.Mockito.mock(PluginContext.class);
        var settingFetcher = org.mockito.Mockito.mock(ReactiveSettingFetcher.class);
        var context = org.mockito.Mockito.mock(ITemplateContext.class);
        var model = org.mockito.Mockito.mock(IModel.class);
        var modelFactory = org.mockito.Mockito.mock(IModelFactory.class);
        var text = org.mockito.Mockito.mock(IText.class);

        when(pluginContext.getVersion()).thenReturn("1.0.0");
        when(settingFetcher.fetch(HomewardGeneralSetting.GROUP, HomewardGeneralSetting.class))
            .thenReturn(Mono.empty());
        when(settingFetcher.fetch(HomewardDisplaySetting.GROUP, HomewardDisplaySetting.class))
            .thenReturn(Mono.empty());
        when(context.getVariable("error")).thenReturn(Map.of("status", 404));
        when(context.getModelFactory()).thenReturn(modelFactory);
        when(modelFactory.createText(anyString())).thenReturn(text);

        new HomewardHeadProcessor(pluginContext, settingFetcher)
            .process(context, model, null)
            .block(Duration.ofSeconds(1));

        var html = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(modelFactory).createText(html.capture());
        verify(model).add(text);
        assertThat(html.getValue())
            .contains("/plugins/homeward/assets/static/homeward/homeward-public.css?v=1.0.0")
            .contains("window.__HOMEWARD_404__ = true")
            .contains("/plugins/homeward/assets/static/homeward/homeward-public.js?v=1.0.0");
    }

    @Test
    void skipsWhenContextIsNot404() {
        var pluginContext = org.mockito.Mockito.mock(PluginContext.class);
        var settingFetcher = org.mockito.Mockito.mock(ReactiveSettingFetcher.class);
        var context = org.mockito.Mockito.mock(ITemplateContext.class);
        var model = org.mockito.Mockito.mock(IModel.class);

        when(context.getVariable("error")).thenReturn(Map.of("status", 500));

        new HomewardHeadProcessor(pluginContext, settingFetcher)
            .process(context, model, null)
            .block(Duration.ofSeconds(1));

        verify(model, never()).add(org.mockito.Mockito.any());
    }
}
