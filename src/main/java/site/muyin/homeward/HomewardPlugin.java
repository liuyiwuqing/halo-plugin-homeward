package site.muyin.homeward;

import org.springframework.stereotype.Component;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpecs;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;
import site.muyin.homeward.scheme.CaseApplication;
import site.muyin.homeward.scheme.ClueReport;
import site.muyin.homeward.scheme.MissingPersonCase;

/**
 * 归航寻亲插件生命周期入口。
 *
 * @author lywq
 * @since 1.0.0
 */
@Component
public class HomewardPlugin extends BasePlugin {

    private final SchemeManager schemeManager;

    public HomewardPlugin(PluginContext pluginContext, SchemeManager schemeManager) {
        super(pluginContext);
        this.schemeManager = schemeManager;
    }

    @Override
    public void start() {
        schemeManager.register(MissingPersonCase.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<MissingPersonCase, String>single("spec.slug", String.class)
                .unique(true)
                .nullable(false)
                .indexFunc(item -> item.getSpec() == null ? null : item.getSpec().getSlug()));
            indexSpecs.add(IndexSpecs.<MissingPersonCase, String>single("spec.status", String.class)
                .indexFunc(item -> item.getSpec() == null || item.getSpec().getStatus() == null
                    ? null
                    : item.getSpec().getStatus().name()));
            indexSpecs.add(IndexSpecs.<MissingPersonCase, String>single("spec.title", String.class)
                .indexFunc(item -> item.getSpec() == null ? null : item.getSpec().getTitle()));
            indexSpecs.add(IndexSpecs.<MissingPersonCase, String>single("spec.personName", String.class)
                .indexFunc(item -> item.getSpec() == null ? null : item.getSpec().getPersonName()));
            indexSpecs.add(IndexSpecs.<MissingPersonCase, String>single("spec.missingLocation", String.class)
                .indexFunc(item -> item.getSpec() == null ? null : item.getSpec().getMissingLocation()));
            indexSpecs.add(IndexSpecs.<MissingPersonCase, Integer>single("spec.priority", Integer.class)
                .indexFunc(item -> item.getSpec() == null ? null : item.getSpec().getPriority()));
            indexSpecs.add(IndexSpecs.<MissingPersonCase, java.time.Instant>single(
                    "spec.publishedAt", java.time.Instant.class)
                .indexFunc(item -> item.getSpec() == null ? null : item.getSpec().getPublishedAt()));
            indexSpecs.add(IndexSpecs.<MissingPersonCase, java.time.Instant>single(
                    "spec.updatedAt", java.time.Instant.class)
                .indexFunc(item -> item.getSpec() == null ? null : item.getSpec().getUpdatedAt()));
        });

        schemeManager.register(ClueReport.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<ClueReport, String>single("spec.receiptTokenHash", String.class)
                .unique(true).nullable(true)
                .indexFunc(item -> item.getSpec() == null ? null : item.getSpec().getReceiptTokenHash()));
            indexSpecs.add(IndexSpecs.<ClueReport, String>single("spec.caseSlug", String.class)
                .indexFunc(item -> item.getSpec() == null ? null : item.getSpec().getCaseSlug()));
            indexSpecs.add(IndexSpecs.<ClueReport, String>single("spec.caseTitle", String.class)
                .indexFunc(item -> item.getSpec() == null ? null : item.getSpec().getCaseTitle()));
            indexSpecs.add(IndexSpecs.<ClueReport, String>single("spec.submitterName", String.class)
                .indexFunc(item -> item.getSpec() == null ? null : item.getSpec().getSubmitterName()));
            indexSpecs.add(IndexSpecs.<ClueReport, String>single("spec.contact", String.class)
                .indexFunc(item -> item.getSpec() == null ? null : item.getSpec().getContact()));
            indexSpecs.add(IndexSpecs.<ClueReport, String>single("spec.content", String.class)
                .indexFunc(item -> item.getSpec() == null ? null : item.getSpec().getContent()));
            indexSpecs.add(IndexSpecs.<ClueReport, String>single("spec.status", String.class)
                .indexFunc(item -> item.getSpec() == null || item.getSpec().getStatus() == null
                    ? null
                    : item.getSpec().getStatus().name()));
            indexSpecs.add(IndexSpecs.<ClueReport, java.time.Instant>single(
                    "spec.submittedAt", java.time.Instant.class)
                .indexFunc(item -> item.getSpec() == null ? null : item.getSpec().getSubmittedAt()));
        });

        schemeManager.register(CaseApplication.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<CaseApplication, String>single("spec.status", String.class)
                .indexFunc(item -> item.getSpec() == null || item.getSpec().getStatus() == null
                    ? null : item.getSpec().getStatus().name()));
            indexSpecs.add(IndexSpecs.<CaseApplication, java.time.Instant>single("spec.submittedAt", java.time.Instant.class)
                .indexFunc(item -> item.getSpec() == null ? null : item.getSpec().getSubmittedAt()));
        });
    }

    @Override
    public void stop() {
        schemeManager.unregister(Scheme.buildFromType(CaseApplication.class));
        schemeManager.unregister(Scheme.buildFromType(ClueReport.class));
        schemeManager.unregister(Scheme.buildFromType(MissingPersonCase.class));
    }
}
