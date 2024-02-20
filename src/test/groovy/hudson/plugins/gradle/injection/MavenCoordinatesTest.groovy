package hudson.plugins.gradle.injection

import spock.lang.Specification

class MavenCoordinatesTest extends Specification {

    def 'parse coordinates'() {
        when:
        def parsed = MavenCoordinates.parseCoordinates(coordinates)

        then:
        parsed == expected

        where:
        coordinates              | expected
        'my.org.foo:bar-app:1.0' | new MavenCoordinates('my.org.foo', 'bar-app', '1.0')
        'foo:bar'                | new MavenCoordinates('foo', 'bar')
        'foo'                    | null
        'foo:bar:1.0:jar'        | null
        '::'                     | null
        // probably not what we want
        ':foo:bar'               | new MavenCoordinates('', 'foo', 'bar')
    }

}
