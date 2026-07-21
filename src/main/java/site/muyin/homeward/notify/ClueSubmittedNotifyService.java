package site.muyin.homeward.notify;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.notification.Reason;
import run.halo.app.core.extension.notification.Subscription;
import run.halo.app.extension.GroupVersion;
import run.halo.app.notification.NotificationCenter;
import run.halo.app.notification.NotificationReasonEmitter;
import run.halo.app.notification.UserIdentity;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.homeward.scheme.ClueReport;
import site.muyin.homeward.setting.HomewardNotificationSetting;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClueSubmittedNotifyService {

    public static final String REASON_TYPE = "homeward-clue-submitted-notification";

    private final NotificationReasonEmitter notificationReasonEmitter;

    private final NotificationCenter notificationCenter;

    private final ReactiveSettingFetcher settingFetcher;

    public Mono<Void> notifySubmitted(ClueReport clueReport) {
        return settingFetcher.fetch(HomewardNotificationSetting.GROUP, HomewardNotificationSetting.class)
            .defaultIfEmpty(HomewardNotificationSetting.defaults())
            .flatMapMany(setting -> {
                if (!setting.enabledOrDefault()) {
                    return Flux.empty();
                }
                return Flux.fromIterable(setting.recipientsOrDefault());
            })
            .concatMap(recipient -> sendTo(recipient, clueReport)
                .onErrorResume(error -> {
                    log.warn("Failed to send homeward clue notification, recipient={}, clue={}",
                        recipient, clueReport.getMetadata().getName(), error);
                    return Mono.empty();
                }))
            .then();
    }

    private Mono<Void> sendTo(String recipient, ClueReport clueReport) {
        UserIdentity userIdentity = UserIdentity.of(recipient);
        var attributes = attributesOf(clueReport);
        return autoSubscribe(userIdentity)
            .then(notificationReasonEmitter.emit(REASON_TYPE, builder -> builder
                .attributes(attributes)
                .author(userIdentity)
                .subject(Reason.Subject.builder()
                    .apiVersion(new GroupVersion(User.GROUP, User.KIND).toString())
                    .kind(User.KIND)
                    .name(userIdentity.name())
                    .title(attributes.get("title").toString())
                    .url(attributes.get("jumpUrl").toString())
                    .build())))
            .then();
    }

    private Mono<Void> autoSubscribe(UserIdentity userIdentity) {
        var subscriber = new Subscription.Subscriber();
        subscriber.setName(userIdentity.name());
        var interestReason = new Subscription.InterestReason();
        interestReason.setReasonType(REASON_TYPE);
        interestReason.setSubject(Subscription.ReasonSubject.builder()
            .apiVersion(new GroupVersion(User.GROUP, User.KIND).toString())
            .kind(User.KIND)
            .name(userIdentity.name())
            .build());
        return notificationCenter.subscribe(subscriber, interestReason).then();
    }

    private Map<String, Object> attributesOf(ClueReport clueReport) {
        var spec = clueReport.getSpec();
        String clueName = clueReport.getMetadata() == null ? "" : clueReport.getMetadata().getName();
        String title = "【归航寻亲】收到新的寻亲线索";
        String body = "案例：" + spec.getCaseTitle() + "\n提交人："
            + valueOrAnonymous(spec.getSubmitterName()) + "\n联系方式：" + spec.getContact();
        return Map.of(
            "title", title,
            "body", body,
            "caseTitle", spec.getCaseTitle(),
            "caseSlug", spec.getCaseSlug(),
            "clueName", clueName,
            "submitterName", valueOrAnonymous(spec.getSubmitterName()),
            "contact", spec.getContact(),
            "content", spec.getContent(),
            "jumpUrl", "/console/homeward?tab=clues&clue=" + clueName
        );
    }

    private static String valueOrAnonymous(String value) {
        return value == null || value.isBlank() ? "匿名访客" : value;
    }
}
