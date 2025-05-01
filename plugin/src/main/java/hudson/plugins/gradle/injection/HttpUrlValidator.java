package hudson.plugins.gradle.injection;

import org.apache.commons.validator.routines.UrlValidator;

public final class HttpUrlValidator implements Validator<String> {

    private static final HttpUrlValidator INSTANCE = new HttpUrlValidator();

    private static final UrlValidator URL_VALIDATOR =
            new UrlValidator(new String[] {"http", "https"}, UrlValidator.ALLOW_LOCAL_URLS);

    private HttpUrlValidator() {}

    public static HttpUrlValidator getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isValid(String value) {
        return URL_VALIDATOR.isValid(value);
    }
}
