package hudson.plugins.gradle.injection

import hudson.FilePath
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Subject

class MavenExtensionsHandlerTest extends Specification {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder()

    @Subject
    MavenExtensionsHandler mavenExtensionsHandler = new MavenExtensionsHandler()

    def "only copies configuration extension if it doesn't exist"() {
        given:
        def folder = tempFolder.newFolder()
        def root = new FilePath(folder)

        when:
        def firstFilePath = mavenExtensionsHandler.copyExtensionToAgent(MavenExtension.CONFIGURATION, root)

        then:
        firstFilePath.exists()
        def firstLastModified = firstFilePath.lastModified()
        firstLastModified > 0

        when:
        def secondFilePath = mavenExtensionsHandler.copyExtensionToAgent(MavenExtension.CONFIGURATION, root)

        then:
        secondFilePath.exists()
        secondFilePath.remote == firstFilePath.remote
        def secondLastModified = firstFilePath.lastModified()
        secondLastModified == firstLastModified
    }

    def "downloads Develocity/CCUD extensions from the default repository"() {
        given:
        def folder = tempFolder.newFolder()
        def root = new FilePath(folder)

        when:
        def firstFilePath = mavenExtensionsHandler.downloadExtensionToAgent(MavenExtension.DEVELOCITY, "1.22", root)

        then:
        firstFilePath.exists()

        when:
        def secondFilePath = mavenExtensionsHandler.downloadExtensionToAgent(MavenExtension.CCUD, "2.0", root)

        then:
        secondFilePath.exists()
    }

    def "removes all files"() {
        given:
        def folder = tempFolder.newFolder()
        def root = new FilePath(folder)

        when:
        def geExtensionFilePath = mavenExtensionsHandler.downloadExtensionToAgent(MavenExtension.DEVELOCITY, "1.22", root)
        def ccudExtensionFilePath = mavenExtensionsHandler.downloadExtensionToAgent(MavenExtension.CCUD, "2.0", root)

        then:
        geExtensionFilePath.exists()
        ccudExtensionFilePath.exists()

        geExtensionFilePath.getParent().remote == ccudExtensionFilePath.getParent().remote

        when:
        mavenExtensionsHandler.deleteAllExtensionsFromAgent(root)

        then:
        !geExtensionFilePath.exists()
        !ccudExtensionFilePath.exists()
    }
}
