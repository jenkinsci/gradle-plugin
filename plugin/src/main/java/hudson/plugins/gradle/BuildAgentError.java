package hudson.plugins.gradle;

import java.util.Objects;
import org.kohsuke.stapler.export.ExportedBean;

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
