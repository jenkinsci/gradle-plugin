package hudson.plugins.gradle.injection

import hudson.util.FormValidation
import org.junit.ClassRule
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Subject(InjectionConfig.class)
class InjectionConfigTest extends Specification {

    @Shared
    @ClassRule
    JenkinsRule jenkinsRule = new JenkinsRule()

    @Unroll
    def "validates server url"() {
        expect:
        with(InjectionConfig.get().doCheckServer(url)) {
            kind == expectedKind
            message == expectedMssage
        }

        where:
        url                      || expectedKind              | expectedMssage
        "http://gradle.com/test" || FormValidation.Kind.OK    | null
        "http://localhost"       || FormValidation.Kind.OK    | null
        "https://localhost"      || FormValidation.Kind.OK    | null
        "ftp://localhost"        || FormValidation.Kind.ERROR | "Not a valid URL."
        "localhost"              || FormValidation.Kind.ERROR | "Not a valid URL."
        ""                       || FormValidation.Kind.ERROR | "Required."
        null                     || FormValidation.Kind.ERROR | "Required."
    }
}
