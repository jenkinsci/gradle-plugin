package hudson.plugins.gradle.injection

import spock.lang.Specification

class SystemPropertyTest extends Specification {

    def 'parse'() {
        when:
        def parsed = SystemProperty.parse(sysProp)

        then:
        parsed == expected

        where:
        sysProp     | expected
        '-Dfoo=bar' | new SystemProperty(SystemProperty.Key.optional('foo'), 'bar')
        'foo=bar'   | null
        '-Dfoo='    | new SystemProperty(SystemProperty.Key.optional('foo'), '')
    }

}
