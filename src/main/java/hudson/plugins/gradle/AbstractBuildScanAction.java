package hudson.plugins.gradle;

import hudson.model.Action;
import hudson.model.Actionable;
import hudson.plugins.gradle.enriched.ScanDetail;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@ExportedBean
public abstract class AbstractBuildScanAction implements Action {

    protected transient Actionable target;
    private List<String> scanUrls = new ArrayList<>();
    private final List<ScanDetail> scanDetails = new ArrayList<>();
    @Nullable
    private BuildAgentError buildAgentError;

    // Backward compatibility for old plugins versions which created an action per-scan
    private transient String scanUrl;

    @Override
    public String getIconFileName() {
        return "/plugin/gradle/images/svgs/gradle-build-scan.svg";
    }

    @Override
    public String getDisplayName() {
        return "Build Scan";
    }

    @Override
    public String getUrlName() {
        return "buildScan";
    }

    public void addBuildAgentError(BuildAgentError buildAgentError) {
        // Capture only the first error for now
        if (this.buildAgentError == null) {
            this.buildAgentError = buildAgentError;
        }
    }

    public void addScanUrls(Collection<String> scanUrls, Function<String, Optional<ScanDetail>> scanDetailsFactory) {
        for (String scanUrl : scanUrls) {
            addScanUrl(scanUrl);
            scanDetailsFactory.apply(scanUrl).ifPresent(this::addScanDetail);
        }
    }

    public void addScanUrl(String scanUrl) {
        if (!scanUrls.contains(scanUrl)) {
            scanUrls.add(scanUrl);
        }
    }

    public void addScanDetail(ScanDetail scanDetail) {
        if (!scanDetails.contains(scanDetail)) {
            scanDetails.add(scanDetail);
        }
    }

    @Exported
    public List<String> getScanUrls() {
        return Collections.unmodifiableList(scanUrls);
    }

    @Exported
    public List<ScanDetail> getScanDetails() {
        return Collections.unmodifiableList(scanDetails);
    }

    @CheckForNull
    @Exported
    public BuildAgentError getBuildAgentError() {
        return buildAgentError;
    }

    public Actionable getTarget() {
        return target;
    }

    /**
     * Invoked by XStream when this object is read into memory.
     */
    @SuppressWarnings("unused")
    private Object readResolve() {
        if (scanUrl != null) {
            scanUrls = Collections.singletonList(scanUrl);
        }

        return this;
    }
}
