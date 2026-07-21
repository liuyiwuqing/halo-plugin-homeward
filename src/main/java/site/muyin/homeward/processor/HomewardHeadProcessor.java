package site.muyin.homeward.processor;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.IModel;
import org.thymeleaf.processor.element.IElementModelStructureHandler;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.PluginContext;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.app.theme.dialect.TemplateHeadProcessor;
import site.muyin.homeward.setting.HomewardDisplaySetting;
import site.muyin.homeward.setting.HomewardGeneralSetting;
import site.muyin.homeward.support.HomewardTemplateSupport;

@Component
@RequiredArgsConstructor
public class HomewardHeadProcessor implements TemplateHeadProcessor {

    private static final String ASSET_BASE = "/plugins/homeward/assets/static/homeward";

    private final PluginContext pluginContext;

    private final ReactiveSettingFetcher settingFetcher;

    @Override
    public Mono<Void> process(ITemplateContext context, IModel model,
                              IElementModelStructureHandler structureHandler) {
        String path = HomewardTemplateSupport.requestPath(context);
        if (HomewardTemplateSupport.shouldSkipPath(path) || !HomewardTemplateSupport.isNotFoundContext(context)) {
            return Mono.empty();
        }
        return Mono.zip(generalSetting(), displaySetting())
            .filter(tuple -> tuple.getT1().enabledOrDefault() && tuple.getT2().inject404OrDefault())
            .doOnNext(tuple -> inject(context, model))
            .then();
    }

    private void inject(ITemplateContext context, IModel model) {
        String version = pluginContext.getVersion();
        String html = """
            <link rel="stylesheet" href="%s/homeward-public.css?v=%s" />
            <script>window.__HOMEWARD_404__ = true;</script>
            <script defer src="%s/homeward-public.js?v=%s"></script>
            """.formatted(ASSET_BASE, version, ASSET_BASE, version);
        model.add(context.getModelFactory().createText(html));
    }

    private Mono<HomewardGeneralSetting> generalSetting() {
        return settingFetcher.fetch(HomewardGeneralSetting.GROUP, HomewardGeneralSetting.class)
            .defaultIfEmpty(HomewardGeneralSetting.defaults());
    }

    private Mono<HomewardDisplaySetting> displaySetting() {
        return settingFetcher.fetch(HomewardDisplaySetting.GROUP, HomewardDisplaySetting.class)
            .defaultIfEmpty(HomewardDisplaySetting.defaults());
    }
}
