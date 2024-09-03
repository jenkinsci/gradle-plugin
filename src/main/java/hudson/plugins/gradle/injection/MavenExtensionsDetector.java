package hudson.plugins.gradle.injection;

import hudson.FilePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MavenExtensionsDetector {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenExtensionsDetector.class);

    static Set<MavenExtension> detect(InjectionConfig config, FilePath workspace) throws IOException, InterruptedException {
        if (InjectionUtil.isInvalid(InjectionConfig.checkRequiredVersion(config.getMavenExtensionVersion()))) {
            return Collections.emptySet();
        }

        FilePath extensionsFile = workspace.child(".mvn/extensions.xml");

        if (extensionsFile.exists()) {
            LOGGER.debug("Found extensions file: {}", extensionsFile);

            MavenExtensions mavenExtensions = MavenExtensions.fromFilePath(extensionsFile);

            Set<MavenExtension> knownExtensions = new HashSet<>();
            if (mavenExtensions.hasExtension(MavenExtension.DEVELOCITY.getCoordinates()) ||
                mavenExtensions.hasExtension(MavenExtension.GRADLE_ENTERPRISE.getCoordinates()) ||
                mavenExtensions.hasExtension(MavenCoordinates.parseCoordinates(config.getMavenExtensionCustomCoordinates()))) {
                knownExtensions.add(MavenExtension.DEVELOCITY);
                knownExtensions.add(MavenExtension.GRADLE_ENTERPRISE);
            }
            if (mavenExtensions.hasExtension(MavenExtension.CCUD.getCoordinates()) ||
                mavenExtensions.hasExtension(MavenCoordinates.parseCoordinates(config.getCcudExtensionCustomCoordinates()))
            ) {
                knownExtensions.add(MavenExtension.CCUD);
            }
            return knownExtensions;
        } else {
            LOGGER.debug("Extensions file not found: {}", extensionsFile);
            return Collections.emptySet();
        }
    }
}
