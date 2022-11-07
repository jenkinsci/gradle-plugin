package hudson.plugins.gradle.injection;

public final class SystemProperty {

    private final String key;
    private final String value;

    public SystemProperty(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String asString() {
        return String.format("-D%s=%s", key, value);
    }
}
