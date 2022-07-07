package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static hudson.plugins.gradle.injection.CopyUtil.copyResourceToNode;

public class MavenBuildScanInjection implements BuildScanInjection {

    private static final Logger LOGGER = Logger.getLogger(MavenBuildScanInjection.class.getName());

    private static final String LIB_DIR_PATH = "jenkins-gradle-plugin/lib";
    private static final String GE_MVN_LIB_NAME = "gradle-enterprise-maven-extension-1.14.3.jar";
    private static final String CCUD_LIB_NAME = "common-custom-user-data-maven-extension-1.10.1.jar";
    // Maven system properties passed on the CLI to a Maven build
    private static final String GRADLE_ENTERPRISE_URL_PROPERTY_KEY = "gradle.enterprise.url";
    private static final String GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY = "gradle.enterprise.allowUntrustedServer";
    // Environment variables set in Jenkins Global configuration
    private static final String GRADLE_SCAN_UPLOAD_IN_BACKGROUND_PROPERTY_KEY = "gradle.scan.uploadInBackground";
    private static final String MAVEN_EXT_CLASS_PATH_PROPERTY_KEY = "maven.ext.class.path";
    private static final MavenOptsSetter MAVEN_OPTS_SETTER = new MavenOptsSetter(
        MAVEN_EXT_CLASS_PATH_PROPERTY_KEY,
        GRADLE_SCAN_UPLOAD_IN_BACKGROUND_PROPERTY_KEY,
        GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY,
        GRADLE_ENTERPRISE_URL_PROPERTY_KEY
    );
    private static final String GE_ALLOW_UNTRUSTED_VAR = "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER";
    private static final String GE_URL_VAR = "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL";
    private static final String GE_CCUD_VERSION_VAR = "JENKINSGRADLEPLUGIN_CCUD_EXTENSION_VERSION";


    @Override
    public String getActivationEnvironmentVariableName() {
        return "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_EXTENSION_VERSION";
    }

    @Override
    public void inject(Node node, EnvVars envGlobal, EnvVars envComputer) {
        if (isEnabled(envGlobal)) {
            try {
                if (node == null) {
                    return;
                }

                FilePath rootPath = node.getRootPath();
                if (rootPath == null) {
                    return;
                }

                if (isOn(envGlobal)) {
                    injectMavenExtension(node, rootPath);
                } else {
                    removeMavenExtension(node, rootPath);
                }
            } catch (IllegalStateException e) {
                LOGGER.warning("Error: " + e.getMessage());
            }
        }
    }

    private void injectMavenExtension(Node node, FilePath rootPath) {
        try {
            String cp = constructExtClasspath(rootPath, isUnix(node));
            List<String> mavenOptsKeyValuePairs = new ArrayList<>();
            mavenOptsKeyValuePairs.add(asSystemProperty(MAVEN_EXT_CLASS_PATH_PROPERTY_KEY, cp));
            mavenOptsKeyValuePairs.add(asSystemProperty(GRADLE_SCAN_UPLOAD_IN_BACKGROUND_PROPERTY_KEY, "false"));

            if (getGlobalEnvVar(GE_ALLOW_UNTRUSTED_VAR) != null) {
                mavenOptsKeyValuePairs.add(asSystemProperty(GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY, getGlobalEnvVar(GE_ALLOW_UNTRUSTED_VAR)));
            }
            if (getGlobalEnvVar(GE_URL_VAR) != null) {
                mavenOptsKeyValuePairs.add(asSystemProperty(GRADLE_ENTERPRISE_URL_PROPERTY_KEY, getGlobalEnvVar(GE_URL_VAR)));
            }
            MAVEN_OPTS_SETTER.appendIfMissing(node, mavenOptsKeyValuePairs);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean isUnix(Node node) {
        Computer computer = node.toComputer();
        return computer == null || Boolean.TRUE.equals(computer.isUnix());
    }

    private void removeMavenExtension(Node node, FilePath rootPath) {
        try {
            deleteResourceFromAgent(GE_MVN_LIB_NAME, rootPath);
            deleteResourceFromAgent(CCUD_LIB_NAME, rootPath);
            MAVEN_OPTS_SETTER.remove(node);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private String constructExtClasspath(FilePath rootPath, boolean isUnix) throws IOException, InterruptedException {
        List<FilePath> libs = new LinkedList<>();
        libs.add(copyResourceToAgent(GE_MVN_LIB_NAME, rootPath));
        if (getGlobalEnvVar(GE_CCUD_VERSION_VAR) != null) {
            libs.add(copyResourceToAgent(CCUD_LIB_NAME, rootPath));
        }
        return libs.stream().map(FilePath::getRemote).collect(Collectors.joining(getDelimiter(isUnix)));
    }

    private String getDelimiter(boolean isUnix) {
        return isUnix ? ":" : ";";
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
        copyResourceToNode(lib, resourceName);
        return lib;
    }

    private void deleteResourceFromAgent(String resourceName, FilePath rootPath) throws IOException, InterruptedException {
        FilePath lib = rootPath.child(LIB_DIR_PATH).child(resourceName);
        lib.delete();
    }

}
