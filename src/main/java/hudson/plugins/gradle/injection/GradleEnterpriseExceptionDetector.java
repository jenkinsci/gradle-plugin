package hudson.plugins.gradle.injection;

import hudson.plugins.gradle.BuildToolType;

public interface GradleEnterpriseExceptionDetector {

    BuildToolType getBuildToolType();

    boolean detect(String line);

    final class ByPrefix implements GradleEnterpriseExceptionDetector {

        private final BuildToolType buildToolType;
        private final String prefix;

        public ByPrefix(BuildToolType buildToolType, String prefix) {
            this.buildToolType = buildToolType;
            this.prefix = prefix;
        }

        @Override
        public BuildToolType getBuildToolType() {
            return buildToolType;
        }

        @Override
        public boolean detect(String line) {
            if (line == null || line.isEmpty()) {
                return false;
            }
            return line.startsWith(prefix);
        }
    }
}
