package hudson.plugins.gradle

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Unroll
@Subject(BuildScanLogScanner)
class BuildScanLogScannerTest extends Specification {

    def 'properly captures build scan url given #log'(List<String> log, List<String> expectedUrls) {
        given:
        def listener = new SimpleBuildScanPublishedListener()
        def scanner = new BuildScanLogScanner(listener)

        when:
        log.each { scanner.scanLine(it) }

        then:
        listener.buildScans == expectedUrls

        where:
        log                                                                                                         || expectedUrls
        logWithBuildScans(["https://scans.gradle.com/s/bzb4vn64kx3bc"])                                             || ["https://scans.gradle.com/s/bzb4vn64kx3bc"]
        logWithBuildScans(["https://scans.gradle.com/s/bzb4vn64kx3bc", "https://scans.gradle.com/s/asc9wm73ly1do"]) || ["https://scans.gradle.com/s/bzb4vn64kx3bc", "https://scans.gradle.com/s/asc9wm73ly1do"]
        logWithBuildScans(["https://scans.gradle.com/bzb4vn64kx3bc"])                                               || []
        logWithBuildScans(["https://scans.gradle.com/s/bzb4vn64kx3bc"], 1010)                                       || []
        logWithBuildScans(["https://scans.gradle.com/s/bzb4vn64kx3bc"], 900)                                        || ["https://scans.gradle.com/s/bzb4vn64kx3bc"]
    }

    static List<String> logWithBuildScans(List<String> scanLinks, linesBetween = 10) {
        def log = []

        scanLinks.forEach { url ->
            log.addAll(["Some log", "other log", "https://gradle.com/s/1234567890", "more log", "and more log"])
            log.add("Publishing build scan...")
            (1..linesBetween).each { log.add("Some in-between log") }
            log.add(url)
            log.addAll(["Some log", "other log"])
        }

        log
    }
}
