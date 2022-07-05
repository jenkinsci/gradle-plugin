package hudson.plugins.gradle.injection

import spock.lang.Specification

class CpPatternResourceTest extends Specification {
    def "find resource in test resources dir"() {
        when:
        def resource = new CpPatternResource(~/somefile-4.*\.txt/).resolve()

        then:
        resource == 'somefile-4.5.6.txt'
    }

    def "find resource in pattern-cp.jar"() {
        when:
        def resource = new CpPatternResource(~/somefile-1.*\.txt/).resolve()

        then:
        resource == 'somefile-1.2.3.txt'
    }

    def "unexisting resource"() {
        when:
        new CpPatternResource(~/random_unexisting_file/).resolve()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Could not find resource with filename pattern: random_unexisting_file'
    }

    def "from url to jar path"() {
        when:
        def file = CpPatternResource.toJarPath(new URL('jar:file:/path/to/some.jar!/some/dir'))

        then:
        file == '/path/to/some.jar'
    }

}
