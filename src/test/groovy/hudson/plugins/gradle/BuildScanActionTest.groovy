package hudson.plugins.gradle

import hudson.plugins.gradle.enriched.ScanDetail
import spock.lang.Specification
import spock.lang.Subject

@Subject(BuildScanAction.class)
class BuildScanActionTest extends Specification {

    ScanDetail buildScanDetail() {
        def scanDetail = new ScanDetail("http://foo.com")
        scanDetail.buildToolType = ScanDetail.BuildToolType.GRADLE
        scanDetail.buildToolVersion = "7.6"
        scanDetail.hasFailed = false
        scanDetail.projectName = "foo"
        scanDetail.tasks = [ "clean", "build" ]
        scanDetail
    }

    def "Duplicate scan detail can't be added"() {
        given:
        def scanDetail1 = buildScanDetail()
        def scanDetail2 = buildScanDetail()
        def buildScanAction = new BuildScanAction()

        when:
        buildScanAction.addScanDetail(scanDetail1)
        buildScanAction.addScanDetail(scanDetail2)

        then:
        def scanDetails = buildScanAction.getScanDetails()
        scanDetails.size() == 1
        scanDetails.get(0) == buildScanDetail()
    }
}
