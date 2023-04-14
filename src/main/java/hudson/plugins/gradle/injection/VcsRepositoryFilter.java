package hudson.plugins.gradle.injection;

import com.google.common.collect.ImmutableList;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VcsRepositoryFilter {

    private static final VcsRepositoryFilter EMPTY = new VcsRepositoryFilter(Collections.emptyList(), Collections.emptyList());

    private static final String INCLUSION_QUALIFIER = "+:";
    private static final String EXCLUSION_QUALIFIER = "-:";

    private static final String SEPARATOR = "\n";

    private final List<String> inclusion;
    private final List<String> exclusion;

    private VcsRepositoryFilter(List<String> inclusion, List<String> exclusion) {
        this.inclusion = inclusion;
        this.exclusion = exclusion;
    }

    public static VcsRepositoryFilter of(String rawVcsRepositoryFilter) {
        if (rawVcsRepositoryFilter == null || rawVcsRepositoryFilter.isEmpty()) {
            return EMPTY;
        }

        List<String> inclusionFilters = new ArrayList<>();
        List<String> exclusionFilters = new ArrayList<>();

        String[] parsedFilters = rawVcsRepositoryFilter.split(SEPARATOR);
        for (String singleFilter : parsedFilters) {
            if (singleFilter.startsWith(INCLUSION_QUALIFIER)) {
                inclusionFilters.add(singleFilter.substring(INCLUSION_QUALIFIER.length()).trim());
            } else if (singleFilter.startsWith(EXCLUSION_QUALIFIER)) {
                exclusionFilters.add(singleFilter.substring(EXCLUSION_QUALIFIER.length()).trim());
            }
        }

        return new VcsRepositoryFilter(ImmutableList.copyOf(inclusionFilters), ImmutableList.copyOf(exclusionFilters));
    }

    public boolean isEmpty() {
        return inclusion.isEmpty() && exclusion.isEmpty();
    }

    @SuppressFBWarnings(value="EI_EXPOSE_REP", justification = "Immutable instance is already created in the constructor")
    public List<String> getInclusion() {
        return inclusion;
    }

    @SuppressFBWarnings(value="EI_EXPOSE_REP", justification = "Immutable instance is already created in the constructor")
    public List<String> getExclusion() {
        return exclusion;
    }

}
