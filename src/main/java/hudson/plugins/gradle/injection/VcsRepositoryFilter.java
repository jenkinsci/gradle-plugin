package hudson.plugins.gradle.injection;

import com.google.common.collect.ImmutableList;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Util;

import javax.annotation.CheckForNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class VcsRepositoryFilter {

    public static final VcsRepositoryFilter EMPTY = new VcsRepositoryFilter(null, Collections.emptyList(), Collections.emptyList());

    private static final String INCLUSION_QUALIFIER = "+:";
    private static final String EXCLUSION_QUALIFIER = "-:";

    private static final String SEPARATOR = "\n";

    private final String vcsRepositoryFilter;
    private final List<String> inclusion;
    private final List<String> exclusion;

    private VcsRepositoryFilter(String vcsRepositoryFilter, List<String> inclusion, List<String> exclusion) {
        this.vcsRepositoryFilter = vcsRepositoryFilter;
        this.inclusion = inclusion;
        this.exclusion = exclusion;
    }

    public static VcsRepositoryFilter of(String vcsRepositoryFilter) {
        String filter = Util.fixEmptyAndTrim(vcsRepositoryFilter);

        if (filter == null) {
            return EMPTY;
        }

        List<String> inclusionFilters = new ArrayList<>();
        List<String> exclusionFilters = new ArrayList<>();

        Arrays.stream(filter.split(SEPARATOR))
            .map(Util::fixEmptyAndTrim)
            .filter(Objects::nonNull)
            .forEach(pattern -> {
                if (pattern.startsWith(INCLUSION_QUALIFIER)) {
                    String candidate = Util.fixEmptyAndTrim(pattern.substring(INCLUSION_QUALIFIER.length()));
                    if (candidate != null) {
                        inclusionFilters.add(candidate);
                    }
                } else if (pattern.startsWith(EXCLUSION_QUALIFIER)) {
                    String candidate = Util.fixEmptyAndTrim(pattern.substring(EXCLUSION_QUALIFIER.length()));
                    if (candidate != null) {
                        exclusionFilters.add(candidate);
                    }
                }
            });

        return new VcsRepositoryFilter(
            filter,
            ImmutableList.copyOf(inclusionFilters),
            ImmutableList.copyOf(exclusionFilters)
        );
    }

    public boolean isEmpty() {
        return inclusion.isEmpty() && exclusion.isEmpty();
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Immutable instance is already created in the constructor")
    public List<String> getInclusion() {
        return inclusion;
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Immutable instance is already created in the constructor")
    public List<String> getExclusion() {
        return exclusion;
    }

    @CheckForNull
    public String getVcsRepositoryFilter() {
        return vcsRepositoryFilter;
    }

}
