package hudson.plugins.gradle.injection

import spock.lang.Specification

class MavenExtensionTest extends Specification {

    def "createDownloadUrl allow to override the repository URL"() {
        expect:
        MavenExtension.DEVELOCITY.createDownloadUrl('2.1', repositoryUrl) == URI.create("${expectedRepositoryUrl}/com/gradle/develocity-maven-extension/2.1/develocity-maven-extension-2.1.jar")

        where:
        repositoryUrl                     | expectedRepositoryUrl
        null                              | 'https://repo1.maven.org/maven2'
        'invalid'                         | 'https://repo1.maven.org/maven2'
        'ftp://gradle.com/artifactory'    | 'https://repo1.maven.org/maven2'
        'https://gradle.com/artifactory'  | 'https://gradle.com/artifactory'
        'https://gradle.com/artifactory/' | 'https://gradle.com/artifactory'
        'http://gradle.com/artifactory'   | 'http://gradle.com/artifactory'
        'http://gradle.com/artifactory/'  | 'http://gradle.com/artifactory'
    }
}
