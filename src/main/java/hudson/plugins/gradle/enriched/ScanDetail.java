package hudson.plugins.gradle.enriched;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@ExportedBean
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScanDetail {

    public enum BuildToolType {
        @JsonProperty("gradle")
        GRADLE,
        @JsonProperty("maven")
        MAVEN;

        String getAttributesUrlSuffix() {
            return String.format("/%s-attributes", name().toLowerCase(Locale.ROOT));
        }
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
    private boolean hasFailed;

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
        return hasFailed;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScanDetail that = (ScanDetail) o;
        return hasFailed == that.hasFailed && Objects.equals(url, that.url) && Objects.equals(projectName, that.projectName) && buildToolType == that.buildToolType && Objects.equals(buildToolVersion, that.buildToolVersion) && Objects.equals(tasks, that.tasks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, projectName, buildToolType, buildToolVersion, tasks, hasFailed);
    }
}

