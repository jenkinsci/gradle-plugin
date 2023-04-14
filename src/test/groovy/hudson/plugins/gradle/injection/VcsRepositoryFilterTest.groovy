package hudson.plugins.gradle.injection

import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class VcsRepositoryFilterTest extends Specification {

    def 'should parse vcs filter #filter into inclusion #inclusion and exclusion #exclusion '(
            String filter,
            List<String> inclusion,
            List<String> exclusion
    ) {
        when:
        def vcsRepositoryFilter = VcsRepositoryFilter.of(filter)

        then:
        vcsRepositoryFilter.getInclusion() == inclusion
        vcsRepositoryFilter.getExclusion() == exclusion

        where:
        filter                                                                      | inclusion                                 | exclusion
        null                                                                        | []                                        | []
        ""                                                                          | []                                        | []
        "+:one-inclusion"                                                           | ["one-inclusion"]                         | []
        "+:one-inclusion\n+:second-inclusion"                                       | ["one-inclusion", "second-inclusion"]     | []
        "-:one-exclusion"                                                           | []                                        | ["one-exclusion"]
        "-:one-exclusion\n-:second-exclusion"                                       | []                                        | ["one-exclusion", "second-exclusion"]
        "+:one-inclusion\n-:one-exclusion"                                          | ["one-inclusion"]                         | ["one-exclusion"]
        "+:one-inclusion\n+:second-inclusion\n-:one-exclusion\n-:second-exclusion"  | ["one-inclusion", "second-inclusion"]     | ["one-exclusion", "second-exclusion"]
    }

}
