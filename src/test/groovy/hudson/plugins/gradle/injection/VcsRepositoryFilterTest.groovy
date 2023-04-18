package hudson.plugins.gradle.injection


import spock.lang.Specification
import spock.lang.Unroll

class VcsRepositoryFilterTest extends Specification {

    @Unroll
    def 'repo #url is included by applying #filter'(
        String filter,
        String url,
        Optional<Boolean> result
    ) {
        expect:
        VcsRepositoryFilter.of(filter).isIncluded(url) == result

        where:
        filter                                                                     | url                                     | result
        "test"                                                                     | null                                    | Optional.empty()
        null                                                                       | 'http://foo'                            | Optional.empty()
        ""                                                                         | 'http://foo'                            | Optional.empty()
        "test"                                                                     | 'http://test'                           | Optional.empty()
        "\n \n+: foo\n-:bar"                                                       | 'http://foo'                            | Optional.of(true)
        "+:\n+: \n-:\n-: "                                                         | 'http://foo'                            | Optional.empty()
        "+:foo \n-:bar "                                                           | 'http://bar'                            | Optional.of(false)
        "+:one-inclusion"                                                          | 'http://one-inclusion/foo'              | Optional.of(true)
        "+:one-inclusion\n+:second-inclusion"                                      | 'http://second-inclusion/foo'           | Optional.of(true)
        "-:one-exclusion"                                                          | 'http://one-exclusion/foo'              | Optional.of(false)
        "-:one-exclusion\n-:second-exclusion"                                      | 'http://second-exclusion/foo'           | Optional.of(false)
        "+:one-inclusion\n-:one-exclusion"                                         | 'http://one-inclusion/one-exclusion'    | Optional.of(false)
        "+:one-inclusion\n+:second-inclusion\n-:one-exclusion\n-:second-exclusion" | 'http://one-inclusion/second-exclusion' | Optional.of(false)
        "+:one-inclusion\n-:one-exclusion\n+:second-inclusion\n-:second-exclusion" | 'http://one-inclusion/second-exclusion' | Optional.of(false)
    }

}
