package hudson.plugins.gradle.injection;

public enum InitScriptVariables {
    DEVELOCITY_INJECTION_CUSTOM_VALUE("develocity-injection.custom-value"),
    DEVELOCITY_INIT_SCRIPT_NAME("develocity-injection.init-script-name"),
    DEVELOCITY_INJECTION_ENABLED("develocity-injection.enabled"),
    DEVELOCITY_INJECTION_DEBUG("develocity-injection.debug"),
    GRADLE_PLUGIN_REPOSITORY_URL("develocity-injection.plugin-repository.url"),
    GRADLE_PLUGIN_REPOSITORY_USERNAME("develocity-injection.plugin-repository.username"),
    GRADLE_PLUGIN_REPOSITORY_PASSWORD("develocity-injection.plugin-repository.password"),
    DEVELOCITY_PLUGIN_VERSION("develocity-injection.develocity-plugin.version"),
    CCUD_PLUGIN_VERSION("develocity-injection.ccud-plugin.version"),
    DEVELOCITY_URL("develocity-injection.url"),
    DEVELOCITY_ENFORCE_URL("develocity-injection.enforce-url"),
    DEVELOCITY_ALLOW_UNTRUSTED_SERVER("develocity-injection.allow-untrusted-server"),
    DEVELOCITY_CAPTURE_FILE_FINGERPRINTS("develocity-injection.capture-file-fingerprints"),
    DEVELOCITY_UPLOAD_IN_BACKGROUND("develocity-injection.upload-in-background"),
    DEVELOCITY_TERMS_OF_USE_URL("develocity-injection.terms-of-use.url"),
    DEVELOCITY_TERMS_OF_USE_AGREE("develocity-injection.terms-of-use.agree");

    private final String templateName;

    InitScriptVariables(String templateName) {
        this.templateName = templateName;
    }

    String getTemplateName() {
        return templateName;
    }

    String getEnvVar() {
        return templateName.toUpperCase().replace('.', '_').replace('-', '_');
    }

    String sysProp(String value) {
        return "-D" + getTemplateName() + "=" + value;
    }
}
