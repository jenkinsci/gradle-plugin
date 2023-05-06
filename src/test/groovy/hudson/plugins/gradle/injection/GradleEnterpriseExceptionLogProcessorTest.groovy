package hudson.plugins.gradle.injection

import hudson.model.Actionable
import hudson.plugins.gradle.AbstractGradleLogProcessor
import hudson.plugins.gradle.BuildScanAction
import spock.lang.Specification
import spock.lang.Subject

import java.nio.charset.StandardCharsets

class GradleEnterpriseExceptionLogProcessorTest extends Specification {

    OutputStream bos = new ByteArrayOutputStream()
    Actionable actionable = new TestActionable()
    @Subject
    AbstractGradleLogProcessor processor = new GradleEnterpriseExceptionLogProcessor(bos, StandardCharsets.UTF_8, actionable)

    def "detects Maven extension error"() {
        when:
        processor.processLogLine("[ERROR] Internal error in Gradle Enterprise Maven extension: com.acme.FooBar")

        then:
        with(actionable.getAction(BuildScanAction)) {
            scanUrls.isEmpty()
            scanDetails.isEmpty()
            hasMavenErrors
            !hasGradleErrors
        }
    }

    def "detects Gradle plugin error"() {
        when:
        processor.processLogLine("Internal error in Gradle Enterprise Gradle plugin: com.acme.FooBar")

        then:
        with(actionable.getAction(BuildScanAction)) {
            scanUrls.isEmpty()
            scanDetails.isEmpty()
            !hasMavenErrors
            hasGradleErrors
        }
    }

    def "detects errors for Maven and Gradle"() {
        when:
        processor.processLogLine("Internal error in Gradle Enterprise Gradle plugin: com.acme.FooBar")
        processor.processLogLine("[ERROR] Internal error in Gradle Enterprise Maven extension: com.acme.FooBar")

        then:
        with(actionable.getAction(BuildScanAction)) {
            scanUrls.isEmpty()
            scanDetails.isEmpty()
            hasMavenErrors
            hasGradleErrors
        }
    }

    def "does nothing if log line without an error"() {
        when:
        processor.processLogLine("Starting the build...")

        then:
        actionable.getAction(BuildScanAction) == null
    }

    private static class TestActionable extends Actionable {

        String displayName = "Test"
        String searchUrl = "test"
    }
}
