package hudson.plugins.gradle.injection;

import org.apache.commons.lang3.StringUtils;

public final class GradleEnterpriseAccessKeyValidator implements Validator<String> {

    private static final GradleEnterpriseAccessKeyValidator INSTANCE = new GradleEnterpriseAccessKeyValidator();

    private GradleEnterpriseAccessKeyValidator() {
    }

    @Override
    public boolean isValid(String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }

        String[] entries = value.split(";");

        for (String entry : entries) {
            String[] parts = entry.split("=", 2);
            if (parts.length < 2) {
                return false;
            }

            String servers = parts[0];
            String accessKey = parts[1];

            if (StringUtils.isBlank(servers) || StringUtils.isBlank(accessKey)) {
                return false;
            }

            for (String server : servers.split(",")) {
                if (StringUtils.isBlank(server)) {
                    return false;
                }
            }
        }

        return true;
    }

    public static GradleEnterpriseAccessKeyValidator getInstance() {
        return INSTANCE;
    }
}
