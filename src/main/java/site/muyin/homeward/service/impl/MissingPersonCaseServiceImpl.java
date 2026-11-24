package site.muyin.homeward.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.MetadataUtil;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import site.muyin.homeward.enums.CaseStatus;
import site.muyin.homeward.model.MissingPersonCasePublicVO;
import site.muyin.homeward.model.MissingPersonCaseVO;
import site.muyin.homeward.model.WidgetResponse;
import site.muyin.homeward.query.MissingPersonCasePublicQuery;
import site.muyin.homeward.query.MissingPersonCaseQuery;
import site.muyin.homeward.scheme.MissingPersonCase;
import site.muyin.homeward.service.MissingPersonCaseService;
import site.muyin.homeward.setting.HomewardPublicSettings;
import site.muyin.homeward.support.HomewardNames;

import java.time.Instant;
import java.util.Objects;

import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;

@Service
@RequiredArgsConstructor
public class MissingPersonCaseServiceImpl implements MissingPersonCaseService {

    private final ReactiveExtensionClient client;

    private final HomewardPublicSettings publicSettings;

    @Override
    public Mono<ListResult<MissingPersonCasePublicVO>> listForPublic(MissingPersonCasePublicQuery query) {
        return publicSettings.requireEnabled()
            .then(Mono.defer(() -> client.listBy(MissingPersonCase.class,
                query.toListOptions(), query.toPageRequest())))
            .map(result -> new ListResult<>(result.getPage(), result.getSize(), result.getTotal(),
                result.getItems().stream().map(MissingPersonCasePublicVO::from).toList()));
    }

    @Override
    public Mono<ListResult<MissingPersonCaseVO>> listForConsole(MissingPersonCaseQuery query) {
        return client.listBy(MissingPersonCase.class, query.toListOptions(), query.toPageRequest())
            .map(result -> new ListResult<>(result.getPage(), result.getSize(), result.getTotal(),
                result.getItems().stream().map(MissingPersonCaseVO::from).toList()));
    }

    @Override
    public Mono<MissingPersonCaseVO> getForConsole(String name) {
        return client.fetch(MissingPersonCase.class, name)
            .switchIfEmpty(Mono.error(notFound("寻亲案例不存在")))
            .map(MissingPersonCaseVO::from);
    }

    @Override
    public Mono<MissingPersonCaseVO> create(MissingPersonCase item) {
        return Mono.just(normalizeForCreate(item))
            .flatMap(client::create)
            .map(MissingPersonCaseVO::from);
    }

    @Override
    public Mono<MissingPersonCaseVO> update(MissingPersonCase item) {
        String name = item.getMetadata() == null ? null : item.getMetadata().getName();
        if (!hasText(name)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "metadata.name 不能为空"));
        }
        return client.fetch(MissingPersonCase.class, name)
            .switchIfEmpty(Mono.error(notFound("寻亲案例不存在")))
            .map(old -> normalizeForUpdate(item, old))
            .flatMap(client::update)
            .map(MissingPersonCaseVO::from);
    }

    @Override
    public Mono<MissingPersonCaseVO> updateStatus(String name, CaseStatus status) {
        if (status == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "状态不能为空"));
        }
        return client.fetch(MissingPersonCase.class, name)
            .switchIfEmpty(Mono.error(notFound("寻亲案例不存在")))
            .map(item -> {
                var spec = spec(item);
                spec.setStatus(status);
                Instant now = Instant.now();
                if (status == CaseStatus.PUBLISHED && spec.getPublishedAt() == null) {
                    spec.setPublishedAt(now);
                }
                if (status == CaseStatus.FOUND && spec.getFoundAt() == null) {
                    spec.setFoundAt(now);
                }
                spec.setUpdatedAt(now);
                MetadataUtil.nullSafeLabels(item).put("homeward.muyin.site/status", status.name());
                return item;
            })
            .flatMap(client::update)
            .map(MissingPersonCaseVO::from);
    }

    @Override
    public Mono<MissingPersonCaseVO> delete(String name) {
        return client.fetch(MissingPersonCase.class, name)
            .switchIfEmpty(Mono.error(notFound("寻亲案例不存在")))
            .flatMap(client::delete)
            .map(MissingPersonCaseVO::from);
    }

    @Override
    public Mono<MissingPersonCasePublicVO> getPublishedBySlug(String slug) {
        return publicSettings.requireEnabled().then(Mono.defer(() -> findPublishedBySlug(slug)))
            .map(MissingPersonCasePublicVO::from);
    }

    @Override
    public Mono<WidgetResponse> widget(boolean publicPage) {
        return Mono.zip(publicSettings.general(), publicSettings.display(), publicSettings.protection())
            .flatMap(tuple -> {
                var general = tuple.getT1();
                var display = tuple.getT2();
                var protection = tuple.getT3();
                int size = publicPage
                    ? display.publicPageCaseLimitOrDefault()
                    : display.widgetCaseLimitOrDefault();
                if (!general.enabledOrDefault()) {
                    return Mono.just(WidgetResponse.builder()
                        .enabled(false)
                        .clueSubmissionEnabled(false)
                        .minContentLength(protection.minContentLengthOrDefault())
                        .pageSize(size)
                        .cases(java.util.List.of())
                        .publicPageUrl("/homeward")
                        .build());
                }
                var options = MissingPersonCasePublicQuery.publishedOptions();
                var page = PageRequestImpl.of(1, size,
                    Sort.by("spec.priority").descending().and(Sort.by("spec.publishedAt").descending()));
                return client.listBy(MissingPersonCase.class, options, page)
                    .map(result -> WidgetResponse.builder()
                        .enabled(true)
                        .clueSubmissionEnabled(protection.clueSubmissionEnabledOrDefault())
                        .minContentLength(protection.minContentLengthOrDefault())
                        .pageSize(size)
                        .title(general.getPublicPageTitle())
                        .subtitle(general.getPublicPageSubtitle())
                        .contactText(general.getContactText())
                        .publicPageUrl("/homeward")
                        .cases(result.getItems().stream()
                            .map(MissingPersonCasePublicVO::from)
                            .toList())
                        .build());
            });
    }

    @Override
    public Mono<Long> countByStatus(CaseStatus status) {
        var options = ListOptions.builder()
            .andQuery(equal("spec.status", status.name()))
            .build();
        return client.listAll(MissingPersonCase.class, options, Sort.unsorted()).count();
    }

    @Override
    public Mono<Long> countAll() {
        return client.listAll(MissingPersonCase.class, ListOptions.builder().build(), Sort.unsorted()).count();
    }

    private Mono<MissingPersonCase> findPublishedBySlug(String slug) {
        if (!hasText(slug)) {
            return Mono.error(notFound("寻亲案例不存在"));
        }
        var options = ListOptions.builder(MissingPersonCasePublicQuery.publishedOptions())
            .andQuery(equal("spec.slug", slug))
            .build();
        return client.listBy(MissingPersonCase.class, options, PageRequestImpl.of(1, 1))
            .flatMap(result -> result.getItems().stream().findFirst()
                .map(Mono::just)
                .orElseGet(() -> closedOrNotFound(slug)));
    }

    private Mono<MissingPersonCase> closedOrNotFound(String slug) {
        var options = ListOptions.builder().andQuery(equal("spec.slug", slug))
            .andQuery(equal("spec.status", CaseStatus.FOUND.name()))
            .andQuery(isNull("metadata.deletionTimestamp")).build();
        return client.listBy(MissingPersonCase.class, options, PageRequestImpl.of(1, 1))
            .flatMap(result -> Mono.error(result.getItems().stream()
                .anyMatch(item -> item.getSpec().getPublishedAt() != null)
                    ? new ResponseStatusException(HttpStatus.GONE, "这份寻亲信息已结案，请停止传播旧资料")
                    : notFound("寻亲案例不存在")));
    }

    private MissingPersonCase normalizeForCreate(MissingPersonCase item) {
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "寻亲案例不能为空");
        }
        var spec = spec(item);
        requireText(spec.getPersonName(), "姓名不能为空");
        String seed = firstText(spec.getSlug(), spec.getTitle(), spec.getPersonName(), "case");
        spec.setSlug(HomewardNames.slugOrGenerated(seed, "case"));
        if (!hasText(spec.getTitle())) {
            spec.setTitle(spec.getPersonName() + "的寻亲信息");
        }
        if (spec.getStatus() == null) {
            spec.setStatus(CaseStatus.DRAFT);
        }
        if (spec.getPriority() == null) {
            spec.setPriority(0);
        }
        Instant now = Instant.now();
        spec.setCreatedAt(now);
        spec.setUpdatedAt(now);
        if (spec.getStatus() == CaseStatus.PUBLISHED && spec.getPublishedAt() == null) {
            spec.setPublishedAt(now);
        }
        if (spec.getStatus() == CaseStatus.FOUND && spec.getFoundAt() == null) {
            spec.setFoundAt(now);
        }
        if (item.getMetadata() == null) {
            item.setMetadata(new Metadata());
        }
        item.getMetadata().setName(MissingPersonCase.EXTENSION_NAME_PREFIX + spec.getSlug());
        item.getMetadata().setGenerateName(MissingPersonCase.EXTENSION_NAME_PREFIX);
        MetadataUtil.nullSafeLabels(item).put("homeward.muyin.site/status", spec.getStatus().name());
        MetadataUtil.nullSafeAnnotations(item);
        return item;
    }

    private MissingPersonCase normalizeForUpdate(MissingPersonCase incoming, MissingPersonCase old) {
        var source = spec(incoming);
        var target = spec(old);
        requireText(source.getPersonName(), "姓名不能为空");
        String slug = HomewardNames.slugOrGenerated(source.getSlug(), "case");
        if ((target.getPublishedAt() != null || target.getFoundAt() != null
            || target.getStatus() == CaseStatus.PUBLISHED || target.getStatus() == CaseStatus.FOUND)
            && !Objects.equals(target.getSlug(), slug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已公开案例的访问标识不能修改，以保留分享链接");
        }
        target.setSlug(slug);
        target.setTitle(firstText(source.getTitle(), source.getPersonName() + "的寻亲信息"));
        target.setPersonName(source.getPersonName());
        target.setGender(source.getGender());
        target.setBirthDate(source.getBirthDate());
        target.setMissingDate(source.getMissingDate());
        target.setMissingAge(source.getMissingAge());
        target.setCurrentAge(source.getCurrentAge());
        target.setMissingLocation(source.getMissingLocation());
        target.setHometown(source.getHometown());
        target.setCoverImage(source.getCoverImage());
        target.setGallery(source.getGallery());
        target.setPhysicalFeatures(source.getPhysicalFeatures());
        target.setDescription(source.getDescription());
        target.setContactName(source.getContactName());
        target.setContactPhone(source.getContactPhone());
        target.setSourceName(source.getSourceName());
        target.setSourceUrl(source.getSourceUrl());
        target.setPriority(source.getPriority() == null ? 0 : source.getPriority());
        target.setStatus(Objects.requireNonNullElse(source.getStatus(), CaseStatus.DRAFT));
        Instant now = Instant.now();
        target.setUpdatedAt(now);
        if (target.getCreatedAt() == null) {
            target.setCreatedAt(now);
        }
        if (target.getStatus() == CaseStatus.PUBLISHED && target.getPublishedAt() == null) {
            target.setPublishedAt(now);
        }
        if (target.getStatus() == CaseStatus.FOUND && target.getFoundAt() == null) {
            target.setFoundAt(now);
        }
        MetadataUtil.nullSafeLabels(old).put("homeward.muyin.site/status", target.getStatus().name());
        MetadataUtil.nullSafeAnnotations(old);
        return old;
    }

    private static MissingPersonCase.Spec spec(MissingPersonCase item) {
        if (item.getSpec() == null) {
            item.setSpec(new MissingPersonCase.Spec());
        }
        return item.getSpec();
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static void requireText(String value, String message) {
        if (!hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
