package hudson.plugins.gradle.injection

import hudson.FilePath
import hudson.plugins.gradle.injection.download.AgentDownloadClient
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Subject

class MavenExtensionsHandlerIntegrationTest extends Specification {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder()

    @Subject
    MavenExtensionsHandler mavenExtensionsHandler = new MavenExtensionsHandler()

    AgentDownloadClient downloadClient = new AgentDownloadClient()

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

    def "copies Develocity/CCUD extensions when digest doesn't match"() {
        given:
        def controllerFolder = tempFolder.newFolder()
        def agentFolder = tempFolder.newFolder()
        def controllerRoot = new FilePath(controllerFolder)
        def agentRoot = new FilePath(agentFolder)
        def cacheDirectory = controllerRoot.child(InjectionUtil.DOWNLOAD_CACHE_DIR)

        cacheDirectory.child(MavenExtension.DEVELOCITY.getEmbeddedJarName()).write()
                .withCloseable {
                    downloadClient.download(MavenExtension.DEVELOCITY.createDownloadUrl("2.1"), it)
                }

        cacheDirectory.child(MavenExtension.CCUD.getEmbeddedJarName()).write()
                .withCloseable {
                    downloadClient.download(MavenExtension.CCUD.createDownloadUrl("2.0.1"), it)
                }

        when:
        def firstFilePath = mavenExtensionsHandler.copyExtensionToAgent(MavenExtension.DEVELOCITY, controllerRoot, agentRoot, null)

        then:
        firstFilePath.exists()

        when:
        def secondFilePath = mavenExtensionsHandler.copyExtensionToAgent(MavenExtension.CCUD, controllerRoot, agentRoot, null)

        then:
        secondFilePath.exists()
    }

    def "removes all files"() {
        given:
        def controllerFolder = tempFolder.newFolder()
        def agentFolder = tempFolder.newFolder()
        def controllerRoot = new FilePath(controllerFolder)
        def agentRoot = new FilePath(agentFolder)
        def cacheDirectory = controllerRoot.child(InjectionUtil.DOWNLOAD_CACHE_DIR)

        cacheDirectory.child(MavenExtension.DEVELOCITY.getEmbeddedJarName()).write()
                .withCloseable {
                    downloadClient.download(MavenExtension.DEVELOCITY.createDownloadUrl("2.1"), it)
                }

        cacheDirectory.child(MavenExtension.CCUD.getEmbeddedJarName()).write()
                .withCloseable {
                    downloadClient.download(MavenExtension.CCUD.createDownloadUrl("2.0.1"), it)
                }

        when:
        def firstFilePath = mavenExtensionsHandler.copyExtensionToAgent(MavenExtension.DEVELOCITY, controllerRoot, agentRoot, null)

        then:
        firstFilePath.exists()

        when:
        def secondFilePath = mavenExtensionsHandler.copyExtensionToAgent(MavenExtension.CCUD, controllerRoot, agentRoot, null)

        then:
        secondFilePath.exists()

        firstFilePath.getParent().remote == secondFilePath.getParent().remote

        when:
        mavenExtensionsHandler.deleteAllExtensionsFromAgent(agentRoot)

        then:
        !firstFilePath.exists()
        !secondFilePath.exists()
    }

}
