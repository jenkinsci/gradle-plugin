package hudson.plugins.gradle.injection;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SystemProperty {

    private static final Pattern SYS_PROP_PATTERN = Pattern.compile("-D(.*)=(.*)");
    private final Key key;
    private final String value;

    public SystemProperty(Key key, String value) {
        this.key = key;
        this.value = value;
    }

    public String asString() {
        return String.format("-D%s=%s", key.name, value);
    }

    public Key getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public static SystemProperty parse(String sysProp) {
        Matcher matcher = SYS_PROP_PATTERN.matcher(sysProp);
        if (matcher.matches()) {
            return new SystemProperty(Key.optional(matcher.group(1)), matcher.group(2));
        } else {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SystemProperty that = (SystemProperty) o;
        return Objects.equals(key, that.key) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    public static final class Key {

        public final String name;
        public final boolean required;

        private Key(String name, boolean required) {
            this.name = name;
            this.required = required;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Key key = (Key) o;
            return required == key.required && Objects.equals(name, key.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, required);
        }

        /**
         * System property that is always added to the {@code MAVEN_OPTS}.
         */
        public static Key required(String key) {
            return new Key(key, true);
        }

        /**
         * System property that is added to the {@code MAVEN_OPTS} conditionally.
         */
        public static Key optional(String key) {
            return new Key(key, false);
        }
    }
}
