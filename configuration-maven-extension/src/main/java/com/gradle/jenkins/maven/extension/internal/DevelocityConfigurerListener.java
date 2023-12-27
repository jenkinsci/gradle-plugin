package com.gradle.jenkins.maven.extension.internal;

import com.gradle.maven.extension.api.GradleEnterpriseApi;
import com.gradle.maven.extension.api.GradleEnterpriseListener;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    role = GradleEnterpriseListener.class,
    hint = "develocity-configurer"
)
public class DevelocityConfigurerListener implements GradleEnterpriseListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DevelocityConfigurerListener.class);

    // Have to be in sync with what is set in MavenBuildScanInjection
    private static final String JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL = "JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL";
    private static final String JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER = "JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER";

    @Override
    public void configure(GradleEnterpriseApi api, MavenSession session) {
        if (api.getServer() != null) {
            LOGGER.debug("Develocity server is already configured");
            return;
        }

        String server = System.getenv(JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL);
        if (server == null || server.isEmpty()) {
            LOGGER.warn("Environment variable {} is not set", JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL);
            return;
        }

        api.setServer(server);
        LOGGER.debug("Develocity server URL is set to: {}", server);

        api.getBuildScan().setUploadInBackground(false);

        if (Boolean.parseBoolean(System.getenv(JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER))) {
            api.setAllowUntrustedServer(true);
            LOGGER.debug("Allow communication with a Develocity server using an untrusted SSL certificate");
        }
    }
}
