package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.EnvironmentContributor;
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static hudson.plugins.gradle.injection.MavenOptsHandler.MAVEN_OPTS;

@Extension
public class MavenInjectionEnvironmentContributor extends EnvironmentContributor implements MavenInjectionAware {

    private static final MavenOptsHandler MAVEN_OPTS_HANDLER = new MavenOptsHandler(
            MAVEN_EXT_CLASS_PATH_PROPERTY_KEY,
            BUILD_SCAN_UPLOAD_IN_BACKGROUND_PROPERTY_KEY,
            GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY,
            GRADLE_ENTERPRISE_URL_PROPERTY_KEY
    );

    private final MavenExtensionsHandler extensionsHandler = new MavenExtensionsHandler();

    @Override
    public void buildEnvironmentFor(@Nonnull Run run, @Nonnull EnvVars envs, @Nonnull TaskListener listener) {
        Executor executor = run.getExecutor();
        if (executor == null) {
            return;
        }

        Node node = executor.getOwner().getNode();
        if (node == null) {
            return;
        }

        FilePath nodeRootPath = node.getRootPath();
        if (nodeRootPath == null) {
            return;
        }

        if (!isInjectionEnabled(node)) {
            return;
        }

        InjectionConfig config = InjectionConfig.get();
        String server = config.getServer();

        List<FilePath> extensions = new LinkedList<>();
        extensions.add(extensionsHandler.getExtensionLocation(MavenExtensionsHandler.MavenExtension.GRADLE_ENTERPRISE, nodeRootPath));
        if (config.isInjectCcudExtension()) {
            extensions.add(extensionsHandler.getExtensionLocation(MavenExtensionsHandler.MavenExtension.CCUD, nodeRootPath));
        }

        boolean isUnix = isUnix(node);

        List<SystemProperty> systemProperties = new ArrayList<>();
        systemProperties.add(new SystemProperty(MAVEN_EXT_CLASS_PATH_PROPERTY_KEY, constructExtClasspath(extensions, isUnix)));
        systemProperties.add(new SystemProperty(BUILD_SCAN_UPLOAD_IN_BACKGROUND_PROPERTY_KEY, "false"));

        systemProperties.add(new SystemProperty(GRADLE_ENTERPRISE_URL_PROPERTY_KEY, server));
        if (config.isAllowUntrusted()) {
            systemProperties.add(new SystemProperty(GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY, "true"));
        }

        envs.put(MAVEN_OPTS, MAVEN_OPTS_HANDLER.create(envs, systemProperties));

        // Configuration needed to support https://plugins.jenkins.io/maven-plugin/
        extensions.add(extensionsHandler.getExtensionLocation(MavenExtensionsHandler.MavenExtension.CONFIGURATION, nodeRootPath));

        envs.put(JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH, constructExtClasspath(extensions, isUnix));
        envs.put(JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL, server);
        if (config.isAllowUntrusted()) {
            envs.put(JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER, "true");
        } else {
            envs.remove(JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER);
        }
    }

    private static String constructExtClasspath(List<FilePath> extensions, boolean isUnix) {
        return extensions
                .stream()
                .map(FilePath::getRemote)
                .collect(Collectors.joining(getDelimiter(isUnix)));
    }

    private static String getDelimiter(boolean isUnix) {
        return isUnix ? ":" : ";";
    }

    private static boolean isUnix(Node node) {
        Computer computer = node.toComputer();

        return computer == null || Boolean.TRUE.equals(computer.isUnix());
    }

}
