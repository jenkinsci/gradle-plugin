package hudson.plugins.gradle.injection

import hudson.FilePath
import spock.lang.Specification
import spock.lang.TempDir

class MavenExtensionsTest extends Specification {

    private static final SOME_EXTENSION_COORDINATES = new MavenCoordinates('com.gradle', 'some-artifact-id', '0.1.0')

    @TempDir
    File extensionsDir
    File extensionsXml

    def setup() {
        extensionsXml = new File(extensionsDir, 'extensions.xml')
    }

    def 'hasExtension returns false when MavenExtensions is created via empty'() {
        when:
        def mavenExtensions = MavenExtensions.empty()

        then:
        !mavenExtensions.hasExtension(SOME_EXTENSION_COORDINATES)
    }

    def 'hasExtension returns false when extensions xml file is not present'() {
        given:
        extensionsXml.delete()

        when:
        def mavenExtensions = MavenExtensions.fromFilePath(new FilePath(extensionsXml))

        then:
        !mavenExtensions.hasExtension(SOME_EXTENSION_COORDINATES)
    }

    def 'hasExtension returns false when extensions xml file is not valid xml'() {
        given:
        extensionsXml << ""

        when:
        def mavenExtensions = MavenExtensions.fromFilePath(new FilePath(extensionsXml))

        then:
        !mavenExtensions.hasExtension(SOME_EXTENSION_COORDINATES)
    }

    def 'hasExtension returns false when coordinates argument is null'() {
        given:
        MavenCoordinates nullCoords = null
        extensionsXml << generateExtensionsXml(SOME_EXTENSION_COORDINATES)

        when:
        def mavenExtensions = MavenExtensions.fromFilePath(new FilePath(extensionsXml))

        then:
        !mavenExtensions.hasExtension(nullCoords)
    }

    def 'hasExtension returns false when coordinates argument has invalid characters for xpath expression'() {
        given:
        def coordsWithInvalidCharacters = new MavenCoordinates('com.example', "\'")
        extensionsXml << generateExtensionsXml(SOME_EXTENSION_COORDINATES)

        when:
        def mavenExtensions = MavenExtensions.fromFilePath(new FilePath(extensionsXml))

        then:
        !mavenExtensions.hasExtension(coordsWithInvalidCharacters)
    }

    def 'hasExtension returns false when only group or artifact of coordinates argument matches'() {
        given:
        def coordsInExtensionsXml = new MavenCoordinates('com.gradle', 'some-artifact-id', '0.1.0')
        def coordsToMatchSameGroup = new MavenCoordinates('com.gradle', 'different-artifact-d')
        def coordsToMatchSameArtifact = new MavenCoordinates('com.different', 'some-artifact-id')
        extensionsXml << generateExtensionsXml(coordsInExtensionsXml)

        when:
        def mavenExtensions = MavenExtensions.fromFilePath(new FilePath(extensionsXml))

        then:
        !mavenExtensions.hasExtension(coordsToMatchSameGroup)
        !mavenExtensions.hasExtension(coordsToMatchSameArtifact)
    }

    def 'hasExtension returns true when group and artifact of coordinates argument matches'() {
        given:
        def coordinatesInExtensionsXml = new MavenCoordinates('com.gradle', 'some-artifact-id', '0.1.0')
        def coordinatesToMatch = new MavenCoordinates('com.gradle', 'some-artifact-id', '0.2.0')
        extensionsXml << generateExtensionsXml(coordinatesInExtensionsXml)

        when:
        def mavenExtensions = MavenExtensions.fromFilePath(new FilePath(extensionsXml))

        then:
        mavenExtensions.hasExtension(coordinatesToMatch)
    }

    def 'hasExtension returns true when group, artifact, and version of coordinates argument matches'() {
        given:
        def coordinatesInExtensionsXml = new MavenCoordinates('com.example', 'example-artifact-id', '0.1.0')
        extensionsXml << generateExtensionsXml(SOME_EXTENSION_COORDINATES, coordinatesInExtensionsXml)

        when:
        def mavenExtensions = MavenExtensions.fromFilePath(new FilePath(extensionsXml))

        then:
        mavenExtensions.hasExtension(SOME_EXTENSION_COORDINATES)
        mavenExtensions.hasExtension(coordinatesInExtensionsXml)
    }

    def generateExtensionsXml(MavenCoordinates... extensions) {
        """<?xml version="1.0" encoding="UTF-8"?>
            <extensions>
                ${extensions.collect {
            extension ->
                """<extension>
                           <groupId>${extension.groupId}</groupId>
                           <artifactId>${extension.artifactId}</artifactId>
                           <version>${extension.version}</version>
                       </extension>"""
        }.join("\n")}
            </extensions>"""
    }

}
