package hudson.plugins.gradle;

import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@WithPlugins("gradle")
public class InjectionConfigTest extends AbstractAcceptanceTest {

    @Test
    @WithPlugins("git")
    public void readsVcsRepositoryFilters() {
        // given
        String expectedFilters = "+:gradle\n+:maven";
        enableBuildScansForMaven();

        // when
        String originalFilters = getGitRepositoryFilters();

        // then
        assertThat(originalFilters, is(emptyString()));

        // when
        setGitRepositoryFilters(expectedFilters);
        String filters = getGitRepositoryFilters();

        // then
        assertThat(filters, is(equalTo(expectedFilters)));
    }
}
