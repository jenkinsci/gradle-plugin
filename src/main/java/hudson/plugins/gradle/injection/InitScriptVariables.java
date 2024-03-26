package hudson.plugins.gradle.injection;

public enum InitScriptVariables {
    DEVELOCITY_AUTO_INJECTION_CUSTOM_VALUE("develocity.auto-injection.custom-value"),
    DEVELOCITY_INIT_SCRIPT_NAME("develocity.injection.init-script-name"),
    DEVELOCITY_INJECTION_ENABLED("develocity.injection-enabled"),
    GRADLE_PLUGIN_REPOSITORY_URL("gradle.plugin-repository.url"),
    GRADLE_PLUGIN_REPOSITORY_USERNAME("gradle.plugin-repository.username"),
    GRADLE_PLUGIN_REPOSITORY_PASSWORD("gradle.plugin-repository.password"),
    DEVELOCITY_PLUGIN_VERSION("develocity.plugin.version"),
    CCUD_PLUGIN_VERSION("develocity.ccud-plugin.version"),
    DEVELOCITY_URL("develocity.url"),
    DEVELOCITY_ENFORCE_URL("develocity.enforce-url"),
    DEVELOCITY_ALLOW_UNTRUSTED_SERVER("develocity.allow-untrusted-server"),
    DEVELOCITY_CAPTURE_TASK_INPUT_FILES("develocity.capture-file-fingerprints"),
    DEVELOCITY_BUILD_SCAN_UPLOAD_IN_BACKGROUND("develocity.build-scan.upload-in-background"),
    DEVELOCITY_TERMS_OF_USE_URL("develocity.terms-of-use.url"),
    DEVELOCITY_TERMS_OF_USE_AGREE("develocity.terms-of-use.agree");

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
