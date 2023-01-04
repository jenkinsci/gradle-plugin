package hudson.plugins.gradle.enriched

import spock.lang.Specification

class ScanDetailServiceDefaultImplTest extends Specification {

    def 'build ScanDetail with ScanDetailBuilder'() {
        given:
        String projectName = "project"
        String buildToolType = "Gradle"
        String buildToolVersion = "7.5.1"
        String requestedTasks = "build"
        String hasFailed = "false"
        String url = "https://foo.bar"

        when:
        def scanDetail = new ScanDetail.ScanDetailBuilder()
                .withProjectName(projectName)
                .withBuildToolType(buildToolType)
                .withBuildToolVersion(buildToolVersion)
                .withRequestedTasks(requestedTasks)
                .withHasFailed(hasFailed)
                .withUrl(url)
                .build()

        then:
        projectName == scanDetail.projectName
        buildToolType == scanDetail.buildToolType
        buildToolVersion == scanDetail.buildToolVersion
        requestedTasks == scanDetail.requestedTasks
        Boolean.valueOf(hasFailed) == scanDetail.hasFailed
        url == scanDetail.url
    }

}
