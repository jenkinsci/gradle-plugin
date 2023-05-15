package hudson.plugins.gradle;

import hudson.model.Action;
import hudson.model.Actionable;
import hudson.plugins.gradle.enriched.ScanDetail;
import hudson.plugins.gradle.util.CollectionUtil;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@ExportedBean
public abstract class AbstractBuildScanAction implements Action {

    protected transient Actionable target;
    private List<String> scanUrls = new ArrayList<>();
    private final List<ScanDetail> scanDetails = new ArrayList<>();
    private final Set<BuildAgentError> buildAgentErrors = new HashSet<>();

    // Backward compatibility for old plugins versions which created an action per-scan
    private transient String scanUrl;

    @Override
    public String getIconFileName() {
        return "/plugin/gradle/images/svgs/gradle-build-scan.svg";
    }

    @Override
    public String getDisplayName() {
        return "Build Scans";
    }

    @Override
    public String getUrlName() {
        return "buildScans";
    }

    public void addBuildAgentError(BuildAgentError buildAgentError) {
        buildAgentErrors.add(buildAgentError);
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
        return CollectionUtil.unmodifiableCopy(scanUrls);
    }

    @Exported
    public List<ScanDetail> getScanDetails() {
        return CollectionUtil.unmodifiableCopy(scanDetails);
    }

    @Exported
    public boolean getHasMavenErrors() {
        return hasError(BuildToolType.MAVEN);
    }

    @Exported
    public boolean getHasGradleErrors() {
        return hasError(BuildToolType.GRADLE);
    }

    private boolean hasError(BuildToolType buildToolType) {
        return buildAgentErrors.stream().anyMatch(e -> e.getBuildToolType() == buildToolType);
    }

    // Used in the summary.jelly
    @SuppressWarnings("unused")
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
