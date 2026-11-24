package site.muyin.homeward.setting;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;

/** 公开入口共用设置读取，读取失败时不开放数据或提交。 */
@Component
@RequiredArgsConstructor
public class HomewardPublicSettings {

    private final ReactiveSettingFetcher settingFetcher;

    public Mono<Void> requireEnabled() {
        return general().flatMap(setting -> setting.enabledOrDefault()
            ? Mono.empty()
            : Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "归航寻亲暂未开放")));
    }

    public Mono<HomewardGeneralSetting> general() {
        return settingFetcher.fetch(HomewardGeneralSetting.GROUP, HomewardGeneralSetting.class)
            .defaultIfEmpty(HomewardGeneralSetting.defaults());
    }

    public Mono<HomewardDisplaySetting> display() {
        return settingFetcher.fetch(HomewardDisplaySetting.GROUP, HomewardDisplaySetting.class)
            .defaultIfEmpty(HomewardDisplaySetting.defaults());
    }

    public Mono<HomewardProtectionSetting> protection() {
        return settingFetcher.fetch(HomewardProtectionSetting.GROUP, HomewardProtectionSetting.class)
            .defaultIfEmpty(HomewardProtectionSetting.defaults());
    }
}
