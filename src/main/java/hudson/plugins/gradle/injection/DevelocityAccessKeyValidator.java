package hudson.plugins.gradle.injection;

import com.google.common.base.Strings;

public final class DevelocityAccessKeyValidator implements Validator<String> {

    private static final DevelocityAccessKeyValidator INSTANCE = new DevelocityAccessKeyValidator();

    private DevelocityAccessKeyValidator() {
    }

    public static DevelocityAccessKeyValidator getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isValid(String value) {
        if (Strings.isNullOrEmpty(value)) {
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

            if (Strings.isNullOrEmpty(servers) || Strings.isNullOrEmpty(accessKey)) {
                return false;
            }

            for (String server : servers.split(",")) {
                if (Strings.isNullOrEmpty(server)) {
                    return false;
                }
            }
        }

        return true;
    }
}
