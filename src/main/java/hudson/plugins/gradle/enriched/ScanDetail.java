package hudson.plugins.gradle.enriched;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
    @SuppressFBWarnings(value="UWF_UNWRITTEN_FIELD")
    private BuildToolType buildToolType;
    @SuppressFBWarnings(value="UWF_UNWRITTEN_FIELD")
    private String buildToolVersion;
    @JsonAlias({"requestedTasks", "requestedGoals" })
    private List<String> tasks;
    @SuppressFBWarnings(value="UWF_UNWRITTEN_FIELD")
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

    @SuppressFBWarnings(value="EI_EXPOSE_REP")
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

