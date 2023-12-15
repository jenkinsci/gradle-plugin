package hudson.plugins.gradle

import spock.lang.Specification

class TimestampPrefixDetectorTest extends Specification {

    def "detect timestamp on line"() {
        when:
        def prefix = TimestampPrefixDetector.detectTimestampPrefix(line)

        then:
        prefix == expected

        where:
        line                                               | expected
        '[2023-12-08T10:05:56.488Z] > Task :compileJava'   | 27
        '[2023-12-08T10:05:56.488Z] > Task :compileJava\n' | 27
        '[2023-12-08T10:05:56.488Z]> Task :compileJava'    | 0
        '[2023-12-08T10:05:56] > Task :compileJava'        | 0
        'some message'                                     | 0
    }

    def "trim timestamp"() {
        when:
        def trimmed = TimestampPrefixDetector.trimTimestampPrefix(prefix, line)

        then:
        trimmed == expected

        where:
        line                                             | prefix | expected
        '[2023-12-08T10:05:56.488Z] > Task :compileJava' | 27     | '> Task :compileJava'
        '> Task :compileJava'                            | 0      | '> Task :compileJava'
    }

}
