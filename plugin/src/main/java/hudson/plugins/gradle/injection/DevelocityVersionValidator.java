package hudson.plugins.gradle.injection;

import java.util.regex.Pattern;

public final class DevelocityVersionValidator implements Validator<String> {

    private static final DevelocityVersionValidator INSTANCE = new DevelocityVersionValidator();

    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+(\\.\\d+)?(-[-\\w]+)?$");

    private DevelocityVersionValidator() {
    }

    public static DevelocityVersionValidator getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isValid(String value) {
        return VERSION_PATTERN.matcher(value).matches();
    }
}
