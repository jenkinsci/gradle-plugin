package hudson.plugins.gradle.injection;

import com.google.common.base.Strings;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DevelocityAccessCredentials {

    private static final String KEY_DELIMITER = ";";
    private static final String HOST_DELIMITER = "=";
    private final List<HostnameAccessKey> keys;

    private DevelocityAccessCredentials(List<HostnameAccessKey> keys) {
        this.keys = keys;
    }

    public static DevelocityAccessCredentials of(List<HostnameAccessKey> keys) {
        return new DevelocityAccessCredentials(keys);
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    public boolean isSingleKey() {
        return keys.size() == 1;
    }

    public Optional<HostnameAccessKey> find(String host) {
        return keys.stream().filter(k -> k.hostname.equals(host)).findFirst();
    }

    public String getRaw() {
        return keys.stream().map(HostnameAccessKey::getRaw).collect(Collectors.joining(KEY_DELIMITER));
    }

    public Stream<HostnameAccessKey> stream() {
        return keys.stream();
    }

    public static DevelocityAccessCredentials parse(String rawAccessKey) {
        return new DevelocityAccessCredentials(Arrays.stream(rawAccessKey.split(KEY_DELIMITER))
            .map(k -> k.split("="))
            .filter(hostKey -> hostKey.length == 2)
            .map(hostKey -> new HostnameAccessKey(hostKey[0], hostKey[1]))
            .collect(Collectors.toList()));
    }

    public static class HostnameAccessKey {
        private final String hostname;
        private final String key;
        private HostnameAccessKey(String hostname, String key) {
            this.hostname = hostname;
            this.key = key;
        }

        public static HostnameAccessKey of(String hostname, String key) {
            return new HostnameAccessKey(hostname, key);
        }

        public String getHostname() {
            return hostname;
        }

        public String getKey() {
            return key;
        }

        public String getRaw() {
            return hostname + HOST_DELIMITER + key;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HostnameAccessKey that = (HostnameAccessKey) o;
            return Objects.equals(hostname, that.hostname) && Objects.equals(key, that.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(hostname, key);
        }
    }

    public static boolean isValid(String value) {
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
