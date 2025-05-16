package hudson.plugins.gradle;

import hudson.Extension;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.ToolInstallation;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Kohsuke Kawaguchi
 */
public class GradleInstaller extends DownloadFromUrlInstaller {

    @DataBoundConstructor
    public GradleInstaller(String id) {
        super(id);
    }

    @Extension
    public static final class DescriptorImpl extends DownloadFromUrlInstaller.DescriptorImpl<GradleInstaller> {

        public String getDisplayName() {
            return "Install from Gradle.org";
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolType == GradleInstallation.class;
        }
    }
}
