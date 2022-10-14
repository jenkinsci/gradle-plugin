package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.plugins.gradle.injection.MavenExtensionsHandler.MavenExtension;
import hudson.plugins.gradle.util.CollectionUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MavenBuildScanInjection implements BuildScanInjection {

    private static final Logger LOGGER = Logger.getLogger(MavenBuildScanInjection.class.getName());

    // Maven system properties passed on the CLI to a Maven build
    private static final String GRADLE_ENTERPRISE_URL_PROPERTY_KEY = "gradle.enterprise.url";
    private static final String GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY = "gradle.enterprise.allowUntrustedServer";
    private static final String GRADLE_SCAN_UPLOAD_IN_BACKGROUND_PROPERTY_KEY = "gradle.scan.uploadInBackground";
    private static final String MAVEN_EXT_CLASS_PATH_PROPERTY_KEY = "maven.ext.class.path";
    private static final String GRADLE_SCAN_CUSTOM_VALUE_CI_AUTO_INJECTION = "scan.value.CI-auto-injection";
    public static final String JENKINS = "Jenkins";

    private static final MavenOptsSetter MAVEN_OPTS_SETTER = new MavenOptsSetter(
        MAVEN_EXT_CLASS_PATH_PROPERTY_KEY,
        GRADLE_SCAN_UPLOAD_IN_BACKGROUND_PROPERTY_KEY,
        GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY,
        GRADLE_ENTERPRISE_URL_PROPERTY_KEY
    );

    private final MavenExtensionsHandler extensionsHandler = new MavenExtensionsHandler();

    @Override
    public boolean isEnabled(Node node) {
        InjectionConfig config = InjectionConfig.get();

        if (!config.isEnabled() || !config.isInjectMavenExtension() || isMissingRequiredParameters(config)) {
            return false;
        }

        Set<String> disabledNodes =
            CollectionUtil.safeStream(config.getMavenInjectionDisabledNodes())
                .map(NodeLabelItem::getLabel)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> enabledNodes =
            CollectionUtil.safeStream(config.getMavenInjectionEnabledNodes())
                .map(NodeLabelItem::getLabel)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return InjectionUtil.isInjectionEnabledForNode(node::getAssignedLabels, disabledNodes, enabledNodes);
    }

    private static boolean isMissingRequiredParameters(InjectionConfig config) {
        String server = config.getServer();

        return InjectionUtil.isInvalid(InjectionConfig.checkRequiredUrl(server));
    }

    @Override
    public void inject(Node node, EnvVars envGlobal, EnvVars envComputer) {
        boolean enabled = isEnabled(node);

        try {
            if (node == null) {
                return;
            }

            FilePath nodeRootPath = node.getRootPath();
            if (nodeRootPath == null) {
                return;
            }

            if (enabled) {
                inject(node, nodeRootPath);
            } else {
                cleanup(node, nodeRootPath);
            }
        } catch (IllegalStateException e) {
            if (enabled) {
                LOGGER.log(Level.WARNING, "Unexpected exception while injecting build scans for Maven", e);
            }
        }
    }

    private void inject(Node node, FilePath nodeRootPath) {
        try {
            InjectionConfig config = InjectionConfig.get();

            LOGGER.info("Injecting Maven extensions " + nodeRootPath);
            List<FilePath> libs = new LinkedList<>();

            libs.add(extensionsHandler.copyExtensionToAgent(MavenExtension.GRADLE_ENTERPRISE, nodeRootPath));
            if (config.isInjectCcudExtension()) {
                libs.add(extensionsHandler.copyExtensionToAgent(MavenExtension.CCUD, nodeRootPath));
            } else {
                extensionsHandler.deleteExtensionFromAgent(MavenExtension.CCUD, nodeRootPath);
            }

            String cp = constructExtClasspath(libs, isUnix(node));
            List<String> mavenOptsKeyValuePairs = new ArrayList<>();
            mavenOptsKeyValuePairs.add(asSystemProperty(MAVEN_EXT_CLASS_PATH_PROPERTY_KEY, cp));
            mavenOptsKeyValuePairs.add(asSystemProperty(GRADLE_SCAN_UPLOAD_IN_BACKGROUND_PROPERTY_KEY, "false"));

            mavenOptsKeyValuePairs.add(asSystemProperty(GRADLE_ENTERPRISE_URL_PROPERTY_KEY, config.getServer()));
            if (config.isAllowUntrusted()) {
                mavenOptsKeyValuePairs.add(asSystemProperty(GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY, "true"));
            }
            mavenOptsKeyValuePairs.add(asSystemProperty(GRADLE_SCAN_CUSTOM_VALUE_CI_AUTO_INJECTION, JENKINS));

            MAVEN_OPTS_SETTER.appendIfMissing(node, mavenOptsKeyValuePairs);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private void cleanup(Node node, FilePath rootPath) {
        try {
            extensionsHandler.deleteAllExtensionsFromAgent(rootPath);
            MAVEN_OPTS_SETTER.remove(node);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private String constructExtClasspath(List<FilePath> libs, boolean isUnix) throws IOException, InterruptedException {
        return libs.stream().map(FilePath::getRemote).collect(Collectors.joining(getDelimiter(isUnix)));
    }

    private String getDelimiter(boolean isUnix) {
        return isUnix ? ":" : ";";
    }

    private static boolean isUnix(Node node) {
        Computer computer = node.toComputer();
        return computer == null || Boolean.TRUE.equals(computer.isUnix());
    }

    private static String asSystemProperty(String sysProp, String value) {
        return "-D" + sysProp + "=" + value;
    }
}
