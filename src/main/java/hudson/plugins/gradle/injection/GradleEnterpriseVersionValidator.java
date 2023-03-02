package hudson.plugins.gradle.injection;

import java.util.regex.Pattern;

public final class GradleEnterpriseVersionValidator implements Validator<String> {

    private static final GradleEnterpriseVersionValidator INSTANCE = new GradleEnterpriseVersionValidator();

    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+(\\.\\d+)?(-[-\\w]+)?$");

    private GradleEnterpriseVersionValidator() {
    }

    public static GradleEnterpriseVersionValidator getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isValid(String value) {
        return VERSION_PATTERN.matcher(value).matches();
    }
}
