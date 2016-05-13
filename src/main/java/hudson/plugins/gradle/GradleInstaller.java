package hudson.plugins.gradle;

import hudson.Extension;
import hudson.model.DownloadService.Downloadable;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.ToolInstallation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

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
        private static final Logger LOGGER = Logger.getLogger(DescriptorImpl.class.getName());

        private volatile boolean downloadTried = false;

        public String getDisplayName() {
            return "Install from Gradle.org";
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolType == GradleInstallation.class;
        }

        // Temporal workaround for JENKINS-32886
        // TODO - Set baseline in which it can be removed
        @Override
        public List<? extends Installable> getInstallables() throws IOException {
            List<? extends Installable> list = super.getInstallables();
            if (list.isEmpty() && !downloadTried && tryDownload()) {
                // Let's try again
                list = super.getInstallables();
            }
            return list;
        }

        /**
         * Tries to download the installer data.
         * @return True if we must ask the superclass for the list of installers again.
         */
        private boolean tryDownload(Downloadable d) {
            if (downloadTried) {
                return false;
            }
            try {
                d.updateNow();
            } catch(IOException e) {
                LOGGER.log(Level.WARNING, String.format("Unable to download [%s]", d.getId()), e);
                return false;
            } finally {
                downloadTried = true;
            }
        }



    }
}
