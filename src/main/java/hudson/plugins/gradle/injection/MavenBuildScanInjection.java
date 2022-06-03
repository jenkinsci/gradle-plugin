package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Node;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MavenBuildScanInjection implements BuildScanInjection {

    private static final String GE_MVN_LIB_NAME = "gradle-enterprise-maven-extension-1.14.2.jar";
    private static final String CCUD_LIB_NAME = "common-custom-user-data-maven-extension-1.10.1.jar";
    // Maven system properties passed on the CLI to a Maven build
    private static final String GRADLE_ENTERPRISE_URL_PROPERTY_KEY = "gradle.enterprise.url";
    private static final String GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY = "gradle.enterprise.allowUntrustedServer";
    // Environment variables set in Jenkins Global configuration
    private static final String GRADLE_SCAN_UPLOAD_IN_BACKGROUND_PROPERTY_KEY = "gradle.scan.uploadInBackground";
    private static final String MAVEN_EXT_CLASS_PATH_PROPERTY_KEY = "maven.ext.class.path";
    private static final String GE_ALLOW_UNTRUSTED_VAR = "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER";
    private static final String GE_URL_VAR = "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL";
    public static final String LIB_DIR_PATH = "jenkins-gradle-plugin/lib";

    @Override
    public String getActivationEnvironmentVariableName() {
        return "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_EXTENSION_VERSION";
    }

    @Override
    public void inject(Node node, EnvVars envGlobal, EnvVars envComputer) {
        if (node == null) {
            return;
        }

        FilePath rootPath = node.getRootPath();
        if (rootPath == null) {
            return;
        }

        if (isEnabled(envGlobal)) {
            injectMavenExtension(rootPath);
        } else {
            removeMavenExtension(rootPath);
        }
    }

    private void injectMavenExtension(FilePath rootPath) {
        try {
            String cp = constructExtClasspath(copyResourceToAgent(GE_MVN_LIB_NAME, rootPath), copyResourceToAgent(CCUD_LIB_NAME, rootPath));
            List<String> mavenOptsKeyValuePairs = new ArrayList<>();
            mavenOptsKeyValuePairs.add(asSystemProperty(MAVEN_EXT_CLASS_PATH_PROPERTY_KEY, cp));
            mavenOptsKeyValuePairs.add(asSystemProperty(GRADLE_SCAN_UPLOAD_IN_BACKGROUND_PROPERTY_KEY, "false"));

            if (getGlobalEnvVar(GE_ALLOW_UNTRUSTED_VAR) != null) {
                mavenOptsKeyValuePairs.add(asSystemProperty(GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY, getGlobalEnvVar(GE_ALLOW_UNTRUSTED_VAR)));
            }
            if (getGlobalEnvVar(GE_URL_VAR) != null) {
                mavenOptsKeyValuePairs.add(asSystemProperty(GRADLE_ENTERPRISE_URL_PROPERTY_KEY, getGlobalEnvVar(GE_URL_VAR)));
            }
            rootPath.act(new SetMavenOpts(mavenOptsKeyValuePairs));
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private void removeMavenExtension(FilePath rootPath) {
        try {
            deleteResourceFromAgent(GE_MVN_LIB_NAME, rootPath);
            deleteResourceFromAgent(CCUD_LIB_NAME, rootPath);
            rootPath.act(new ClearMavenOpts(
                    MAVEN_EXT_CLASS_PATH_PROPERTY_KEY,
                    GRADLE_SCAN_UPLOAD_IN_BACKGROUND_PROPERTY_KEY,
                    GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY,
                    GRADLE_ENTERPRISE_URL_PROPERTY_KEY
            ));
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private String constructExtClasspath(FilePath... libs) {
        return Stream.of(libs).map(FilePath::getRemote).collect(Collectors.joining(":"));
    }

    private String getGlobalEnvVar(String varName) {
        EnvironmentVariablesNodeProperty envProperty = Jenkins.get().getGlobalNodeProperties()
                .get(EnvironmentVariablesNodeProperty.class);
        return envProperty.getEnvVars().get(varName);
    }

    private String asSystemProperty(String sysProp, String value) {
        return "-D" + sysProp + "=" + value;
    }

    private FilePath copyResourceToAgent(String resourceName, FilePath rootPath) throws IOException, InterruptedException {
        FilePath lib = rootPath.child(LIB_DIR_PATH).child(resourceName);
        InputStream libIs = getClass().getResourceAsStream(resourceName);
        if (libIs == null) {
            throw new IllegalStateException("Could not find resource: " + resourceName);
        }
        lib.copyFrom(libIs);
        return lib;
    }

    private void deleteResourceFromAgent(String resourceName, FilePath rootPath) throws IOException, InterruptedException {
        FilePath lib = rootPath.child(LIB_DIR_PATH).child(resourceName);
        lib.delete();
    }

}
