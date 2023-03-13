package hudson.plugins.gradle.injection;

public final class SystemProperty {

    private final Key key;
    private final String value;

    public SystemProperty(Key key, String value) {
        this.key = key;
        this.value = value;
    }

    public String asString() {
        return String.format("-D%s=%s", key.name, value);
    }

    public static final class Key {

        public final String name;
        public final boolean required;

        private Key(String name, boolean required) {
            this.name = name;
            this.required = required;
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
