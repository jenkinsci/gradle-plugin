package hudson.plugins.gradle.injection

import hudson.console.ConsoleNote
import hudson.model.Actionable
import hudson.plugins.gradle.BaseJenkinsIntegrationTest
import hudson.plugins.gradle.BuildScanAction
import hudson.plugins.gradle.BuildScanPublishedListener
import hudson.plugins.gradle.util.RunUtil
import spock.lang.Subject

import java.nio.charset.StandardCharsets

class BuildScanDetectionLogProcessorIntegrationTest extends BaseJenkinsIntegrationTest {

    private static final String PUBLISHING_MESSAGE = "Publishing build scan..."
    private static final String SCAN_URL = "https://gradle.com/s/abc123"
    private static final String SECOND_SCAN_URL = "https://gradle.com/s/def456"

    OutputStream bos = new ByteArrayOutputStream()
    Actionable actionable = new TestActionable()
    BuildScanPublishedListener listener = { String scanUrl ->
        RunUtil.getOrCreateAction(actionable, BuildScanAction.class, BuildScanAction::new).addScanUrl(scanUrl)
    }
    @Subject
    BuildScanDetectionLogProcessor processor = new BuildScanDetectionLogProcessor(bos, StandardCharsets.UTF_8, listener)

    def "detects a build scan URL"() {
        when:
        processor.processLogLine(PUBLISHING_MESSAGE)
        processor.processLogLine(SCAN_URL)

        then:
        with(actionable.getAction(BuildScanAction)) {
            scanUrls == [SCAN_URL]
        }
    }

    def "detects multiple build scan URLs"() {
        when:
        processor.processLogLine(PUBLISHING_MESSAGE)
        processor.processLogLine(SCAN_URL)
        processor.processLogLine(PUBLISHING_MESSAGE)
        processor.processLogLine(SECOND_SCAN_URL)

        then:
        with(actionable.getAction(BuildScanAction)) {
            scanUrls == [SCAN_URL, SECOND_SCAN_URL]
        }
    }

    def "does nothing when no scan is published"() {
        when:
        processor.processLogLine("Starting the build...")
        processor.processLogLine("BUILD SUCCESSFUL")

        then:
        actionable.getAction(BuildScanAction) == null
    }

    def "strips console notes before scanning"() {
        when:
        processor.processLogLine("${ConsoleNote.PREAMBLE_STR}before${ConsoleNote.POSTAMBLE_STR}${PUBLISHING_MESSAGE}${ConsoleNote.PREAMBLE_STR}after${ConsoleNote.POSTAMBLE_STR}")
        processor.processLogLine(SCAN_URL)

        then:
        with(actionable.getAction(BuildScanAction)) {
            scanUrls == [SCAN_URL]
        }
    }

    def "deduplicates scan URLs"() {
        when:
        processor.processLogLine(PUBLISHING_MESSAGE)
        processor.processLogLine(SCAN_URL)
        processor.processLogLine(PUBLISHING_MESSAGE)
        processor.processLogLine(SCAN_URL)

        then:
        with(actionable.getAction(BuildScanAction)) {
            scanUrls == [SCAN_URL]
        }
    }

    def "detects Develocity publishing message variants"() {
        when:
        processor.processLogLine(publishingMessage)
        processor.processLogLine(SCAN_URL)

        then:
        with(actionable.getAction(BuildScanAction)) {
            scanUrls == [SCAN_URL]
        }

        where:
        publishingMessage << [
            "Publishing build scan...",
            "Publishing Build Scan...",
            "Publishing build information...",
            "Publishing build scan to Develocity...",
            "Publishing build information to Develocity..."
        ]
    }

    private static class TestActionable extends Actionable {

        String displayName = "Test"
        String searchUrl = "test"
    }
}
