package hudson.plugins.gradle.injection;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.RootAction;
import hudson.model.Run;
import hudson.plugins.gradle.AbstractBuildScanAction;
import hudson.util.RunList;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerProxy;

import java.util.Date;
import java.util.Iterator;

@Extension
public class GradleEnterpriseErrorsAction implements RootAction, StaplerProxy {
    @Override
    public String getIconFileName() {
        return isVisible() && Jenkins.get().hasPermission(Jenkins.ADMINISTER) ? "/plugin/gradle/images/svgs/gradle-build-scan.svg" : null;
    }

    @Override
    public String getDisplayName() {
        return "Gradle Enterprise";
    }

    @Override
    public String getUrlName() {
        return "gradle_enterprise";
    }

    @Override
    public Object getTarget() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        return isVisible() ? this : null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Iterator<GeErrorModel> getErrors() {
        return RunList.fromJobs((Iterable) Jenkins.get().allItems(Job.class))
            .limit(200)
            .completedOnly()
            .stream()
            .filter(r -> {
                AbstractBuildScanAction action = ((Run) r).getAction(AbstractBuildScanAction.class);
                return action != null && (action.getHasGradleErrors() || action.getHasMavenErrors());
            })
            .map(r -> GeErrorModel.fromRun((Run) r))
            .iterator();
    }

    private boolean isVisible() {
        return InjectionConfig.get().isCheckForBuildAgentErrors();
    }

    public static class GeErrorModel {
        private final String buildTool;
        private final String project;
        private final String buildStatusIconClassName;
        private final String buildStatus;
        private final String buildUrl;
        private final Date start;

        private GeErrorModel(String buildTool, String project, String buildStatusIconClassName, String buildStatus,
                            String buildUrl, Date start) {
            this.buildTool = buildTool;
            this.project = project;
            this.buildStatusIconClassName = buildStatusIconClassName;
            this.buildStatus = buildStatus;
            this.buildUrl = buildUrl;
            this.start = start;
        }

        static GeErrorModel fromRun(Run<?, ?> r) {
            AbstractBuildScanAction action = r.getAction(AbstractBuildScanAction.class);
            return new GeErrorModel(action.getHasGradleErrors() ? "GRADLE" : "MAVEN",
                r.getParent().getFullName(),
                r.getBuildStatusIconClassName(),
                r.getResult() != null ? r.getResult().toString() : "",
                r.getUrl(),
                new Date(r.getStartTimeInMillis()));
        }

        public String getBuildTool() {
            return buildTool;
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
            return start;
        }
    }
}
