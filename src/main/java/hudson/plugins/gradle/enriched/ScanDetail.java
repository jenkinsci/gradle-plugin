package hudson.plugins.gradle.enriched;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.List;

@ExportedBean
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScanDetail {

    public enum BuildToolType {
        @JsonProperty("gradle")
        GRADLE,
        @JsonProperty("maven")
        MAVEN;
    }

    private final String url;

    @JsonAlias({"rootProjectName", "topLevelProjectName" })
    private String projectName;
    private BuildToolType buildToolType;
    private String buildToolVersion;
    @JsonAlias({"requestedTasks", "requestedGoals" })
    private List<String> tasks;
    private String hasFailed;

    ScanDetail(String url) {
        this.url = url;
    }

    public String getProjectName() {
        return projectName;
    }

    public BuildToolType getBuildToolType() {
        return buildToolType;
    }

    public String getBuildToolVersion() {
        return buildToolVersion;
    }

    public List<String> getTasks() {
        return tasks;
    }

    public boolean getHasFailed() {
        return "true".equals(hasFailed);
    }

    public String getUrl() {
        return url;
    }

}

