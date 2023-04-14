package hudson.plugins.gradle.injection

import hudson.Util
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class VcsRepositoryFilterTest extends Specification {

    def 'should parse vcs filter #filter into inclusion #inclusion and exclusion #exclusion'(
        String filter,
        List<String> inclusion,
        List<String> exclusion
    ) {
        expect:
        with(VcsRepositoryFilter.of(filter)) {
            vcsRepositoryFilter == Util.fixEmptyAndTrim(filter)
            inclusion == inclusion
            exclusion == exclusion
        }

        where:
        filter                                                                     | inclusion                             | exclusion
        null                                                                       | []                                    | []
        ""                                                                         | []                                    | []
        "test"                                                                     | []                                    | []
        "\n \n+: foo\n-:bar"                                                       | ["foo"]                               | ["bar"]
        "+:\n+: \n-:\n-: "                                                         | []                                    | []
        "+:foo \n-:bar "                                                           | ["foo"]                               | ["bar"]
        "+:one-inclusion"                                                          | ["one-inclusion"]                     | []
        "+:one-inclusion\n+:second-inclusion"                                      | ["one-inclusion", "second-inclusion"] | []
        "-:one-exclusion"                                                          | []                                    | ["one-exclusion"]
        "-:one-exclusion\n-:second-exclusion"                                      | []                                    | ["one-exclusion", "second-exclusion"]
        "+:one-inclusion\n-:one-exclusion"                                         | ["one-inclusion"]                     | ["one-exclusion"]
        "+:one-inclusion\n+:second-inclusion\n-:one-exclusion\n-:second-exclusion" | ["one-inclusion", "second-inclusion"] | ["one-exclusion", "second-exclusion"]
    }

}
