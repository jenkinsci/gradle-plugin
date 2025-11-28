package hudson.plugins.gradle;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Locale;

public enum BuildToolType {

    @JsonProperty("gradle")
    GRADLE,
    @JsonProperty("maven")
    MAVEN,
    @JsonProperty("npm")
    NPM;

    public String getAttributesUrlSuffix() {
        return String.format("/%s-attributes", name().toLowerCase(Locale.ROOT));
    }
}
