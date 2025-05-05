package hudson.plugins.gradle.injection;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.RootAction;
import hudson.model.Run;
import hudson.plugins.gradle.AbstractBuildScanAction;
import hudson.plugins.gradle.BuildAgentError;
import hudson.plugins.gradle.BuildToolType;
import hudson.util.RunList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerProxy;

@Extension
public class DevelocityErrorsAction implements RootAction, StaplerProxy {
    @Override
    public String getIconFileName() {
        return isVisible() && Jenkins.get().hasPermission(Jenkins.ADMINISTER)
                ? "/plugin/gradle/images/svgs/gradle-build-scan.svg"
                : null;
    }

    @Override
    public String getDisplayName() {
        return "Develocity";
    }

    @Override
    public String getUrlName() {
        return "develocity";
    }

    @Override
    public Object getTarget() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        return isVisible() ? this : null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Iterator<GeErrorModel> getErrors() {
        Stream<Run> stream = RunList.fromJobs((Iterable) Jenkins.get().allItems(Job.class)).completedOnly().stream();
        return stream.map(GeErrorModel::fromRun)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .limit(200)
                .iterator();
    }

    private boolean isVisible() {
        return InjectionConfig.get().isEnabled() && InjectionConfig.get().isCheckForBuildAgentErrors();
    }

    public static class GeErrorModel {
        private final List<BuildToolIcon> buildToolIcons;
        private final String project;
        private final String buildStatusIconClassName;
        private final String buildStatus;
        private final String buildUrl;
        private final Date start;

        private GeErrorModel(
                List<BuildToolIcon> buildToolIcons,
                String project,
                String buildStatusIconClassName,
                String buildStatus,
                String buildUrl,
                Date start) {
            this.buildToolIcons = buildToolIcons;
            this.project = project;
            this.buildStatusIconClassName = buildStatusIconClassName;
            this.buildStatus = buildStatus;
            this.buildUrl = buildUrl;
            this.start = start;
        }

        static Optional<GeErrorModel> fromRun(Run<?, ?> r) {
            AbstractBuildScanAction action = r.getAction(AbstractBuildScanAction.class);
            if (action != null && action.hasErrors()) {
                return Optional.of(new GeErrorModel(
                        BuildToolIcon.buildToolIcons(action.getBuildAgentErrors()),
                        r.getParent().getFullName(),
                        r.getBuildStatusIconClassName(),
                        Optional.ofNullable(r.getResult()).map(Result::toString).orElse(""),
                        r.getUrl(),
                        new Date(r.getStartTimeInMillis())));
            } else {
                return Optional.empty();
            }
        }

        public static class BuildToolIcon {

            private static final Map<BuildToolType, BuildToolIcon> ICONS_FOR_BUILD_TOOLS = ImmutableMap.of(
                    BuildToolType.GRADLE, new BuildToolIcon("Gradle", "gradle-build-scan.svg"),
                    BuildToolType.MAVEN, new BuildToolIcon("Maven", "maven.svg"));

            private final String tooltip;
            private final String icon;

            private BuildToolIcon(String tooltip, String icon) {
                this.tooltip = tooltip;
                this.icon = icon;
            }

            public static List<BuildToolIcon> buildToolIcons(List<BuildAgentError> buildAgentErrors) {
                return buildAgentErrors.stream()
                        .map(e -> ICONS_FOR_BUILD_TOOLS.get(e.getBuildToolType()))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }

            public String getIcon() {
                return icon;
            }

            public String getTooltip() {
                return tooltip;
            }
        }

        public List<BuildToolIcon> getBuildToolIcons() {
            return ImmutableList.copyOf(buildToolIcons);
        }

        public String getProject() {
            return project;
        }

        public String getBuildStatusIconClassName() {
            return buildStatusIconClassName;
        }

        public String getBuildStatus() {
            return buildStatus;
        }

        public String getBuildUrl() {
            return buildUrl;
        }

        public Date getStart() {
            return new Date(start.getTime());
        }
    }
}
