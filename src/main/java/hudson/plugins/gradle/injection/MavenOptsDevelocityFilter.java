package hudson.plugins.gradle.injection;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static hudson.plugins.gradle.injection.MavenExtClasspathUtils.SPACE;
import static hudson.plugins.gradle.injection.MavenExtClasspathUtils.getDelimiter;
import static hudson.plugins.gradle.injection.MavenInjectionAware.*;

public class MavenOptsDevelocityFilter {

    // Maven ext classpath is handled separately
    private final static MavenOptsHandler handler = new MavenOptsHandler(
        BUILD_SCAN_UPLOAD_IN_BACKGROUND_PROPERTY_KEY,
        GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY,
        GRADLE_ENTERPRISE_URL_PROPERTY_KEY
    );
    private final Set<MavenExtension> knownExtensionsAlreadyApplied;
    private final boolean isUnix;

    public MavenOptsDevelocityFilter(Set<MavenExtension> knownExtensionsAlreadyApplied, boolean isUnix) {
        this.knownExtensionsAlreadyApplied = Sets.immutableEnumSet(knownExtensionsAlreadyApplied);
        this.isUnix = isUnix;
    }

    String filter(String mavenOpts, boolean enforceUrl) {
        mavenOpts = removeKnownExtensionsFromExtClasspath(mavenOpts);

        if (knownExtensionsAlreadyApplied.contains(MavenExtension.GRADLE_ENTERPRISE)) {
            Set<String> keysToKeep = new HashSet<>();
            if (enforceUrl) {
                keysToKeep.add(MavenInjectionAware.GRADLE_ENTERPRISE_URL_PROPERTY_KEY.name);
            }
            mavenOpts = Strings.nullToEmpty(handler.removeIfNeeded(mavenOpts, keysToKeep));
        }
        return mavenOpts;
    }

    private String removeKnownExtensionsFromExtClasspath(String mavenOpts) {
        return Arrays.stream(mavenOpts.split(SPACE))
            .map(s -> {
                SystemProperty sysProp = SystemProperty.parse(s);
                return isMavenExtClasspath(sysProp) ? filterExtClassPath(sysProp) : s;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.joining(SPACE));
    }

    private String filterExtClassPath(SystemProperty sysProp) {
        String cp = Arrays.stream(sysProp.getValue().split(getDelimiter(isUnix)))
            .filter(lib -> !isKnownExtension(lib))
            .collect(Collectors.joining(getDelimiter(isUnix)));
        return cp.isEmpty() ? null : new SystemProperty(MAVEN_EXT_CLASS_PATH_PROPERTY_KEY, cp).asString();
    }

    private static boolean isMavenExtClasspath(SystemProperty sysProp) {
        return sysProp != null && MAVEN_EXT_CLASS_PATH_PROPERTY_KEY.name.equals(sysProp.getKey().name);
    }

    private boolean isKnownExtension(String lib) {
        return knownExtensionsAlreadyApplied.stream().map(MavenExtension::getTargetJarName).anyMatch(lib::contains);
    }

}
