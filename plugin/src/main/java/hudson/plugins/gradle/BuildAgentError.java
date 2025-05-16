package hudson.plugins.gradle;

import org.kohsuke.stapler.export.ExportedBean;

import java.util.Objects;

@ExportedBean
public final class BuildAgentError {

    private final BuildToolType buildToolType;

    public BuildAgentError(BuildToolType buildToolType) {
        this.buildToolType = buildToolType;
    }

    public BuildToolType getBuildToolType() {
        return buildToolType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BuildAgentError that = (BuildAgentError) o;
        return buildToolType == that.buildToolType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(buildToolType);
    }
}
