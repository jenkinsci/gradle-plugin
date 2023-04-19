package hudson.plugins.gradle.injection


import spock.lang.Specification
import spock.lang.Unroll

import static hudson.plugins.gradle.injection.VcsRepositoryFilter.Result

class VcsRepositoryFilterTest extends Specification {

    @Unroll
    def 'filter #filter matches url #url'(
        String filter,
        String url,
        Result result
    ) {
        expect:
        VcsRepositoryFilter.of(filter).matches(url) == result

        where:
        filter                                                                     | url                                     | result
        "test"                                                                     | null                                    | Result.NOT_MATCHED
        null                                                                       | 'http://foo'                            | Result.NOT_MATCHED
        ""                                                                         | 'http://foo'                            | Result.NOT_MATCHED
        "test"                                                                     | 'http://test'                           | Result.NOT_MATCHED
        "\n \n+: foo\n-:bar"                                                       | 'http://foo'                            | Result.INCLUDED
        "+:\n+: \n-:\n-: "                                                         | 'http://foo'                            | Result.NOT_MATCHED
        "+:foo \n-:bar "                                                           | 'http://bar'                            | Result.EXCLUDED
        "+:one-inclusion"                                                          | 'http://one-inclusion/foo'              | Result.INCLUDED
        "+:one-inclusion\n+:second-inclusion"                                      | 'http://second-inclusion/foo'           | Result.INCLUDED
        "-:one-exclusion"                                                          | 'http://one-exclusion/foo'              | Result.EXCLUDED
        "-:one-exclusion\n-:second-exclusion"                                      | 'http://second-exclusion/foo'           | Result.EXCLUDED
        "+:one-inclusion\n-:one-exclusion"                                         | 'http://one-inclusion/one-exclusion'    | Result.EXCLUDED
        "+:one-inclusion\n+:second-inclusion\n-:one-exclusion\n-:second-exclusion" | 'http://one-inclusion/second-exclusion' | Result.EXCLUDED
        "+:one-inclusion\n-:one-exclusion\n+:second-inclusion\n-:second-exclusion" | 'http://one-inclusion/second-exclusion' | Result.EXCLUDED
    }

}
