package hudson.plugins.gradle.injection

import hudson.console.ConsoleNote
import hudson.model.Actionable
import hudson.plugins.gradle.BuildScanAction
import spock.lang.Specification
import spock.lang.Subject

import java.nio.charset.StandardCharsets

class GradleEnterpriseExceptionLogProcessorTest extends Specification {

    private static final String GRADLE_PLUGIN_ERROR = "Internal error in Gradle Enterprise Gradle plugin: com.acme.FooBar"
    private static final String MAVEN_EXTENSION_ERROR = "[ERROR] Internal error in Gradle Enterprise Maven extension: com.acme.FooBar"

    OutputStream bos = new ByteArrayOutputStream()
    Actionable actionable = new TestActionable()
    @Subject
    GradleEnterpriseExceptionLogProcessor processor = new GradleEnterpriseExceptionLogProcessor(bos, StandardCharsets.UTF_8, actionable)

    def "detects Maven extension error"() {
        when:
        processor.processLogLine(line)

        then:
        with(actionable.getAction(BuildScanAction)) {
            scanUrls.isEmpty()
            scanDetails.isEmpty()
            hasMavenErrors
            !hasGradleErrors
        }

        where:
        line << [
            MAVEN_EXTENSION_ERROR,
            "${ConsoleNote.PREAMBLE_STR}before${ConsoleNote.POSTAMBLE_STR}${MAVEN_EXTENSION_ERROR}${ConsoleNote.PREAMBLE_STR}after${ConsoleNote.POSTAMBLE_STR}"
        ]
    }

    def "detects Gradle plugin error"() {
        when:
        processor.processLogLine(GRADLE_PLUGIN_ERROR)

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
        processor.processLogLine(GRADLE_PLUGIN_ERROR)
        processor.processLogLine(MAVEN_EXTENSION_ERROR)

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
