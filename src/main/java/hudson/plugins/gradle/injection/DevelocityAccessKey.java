package hudson.plugins.gradle.injection;

import com.google.common.base.Strings;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public class DevelocityAccessKey {
    private final String hostname;
    private final String key;

    private DevelocityAccessKey(String hostname, String key) {
        this.hostname = hostname;
        this.key = key;
    }

    public static DevelocityAccessKey of(String hostname, String key) {
        return new DevelocityAccessKey(hostname, key);
    }


    public static Optional<DevelocityAccessKey> parse(String rawAccessKey, String host) {
        return Arrays.stream(rawAccessKey.split(";"))
            .map(k -> k.split("="))
            .filter(hostKey -> hostKey[0].equals(host))
            .map(hostKey -> new DevelocityAccessKey(hostKey[0], hostKey[1]))
            .findFirst();
    }

    public String getRawAccessKey() {
        return hostname + "=" + key;
    }

    public String getHostname() {
        return hostname;
    }

    public String getKey() {
        return key;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DevelocityAccessKey that = (DevelocityAccessKey) o;
        return Objects.equals(hostname, that.hostname) && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, key);
    }
}
