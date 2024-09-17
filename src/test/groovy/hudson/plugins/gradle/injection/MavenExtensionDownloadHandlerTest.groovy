package hudson.plugins.gradle.injection

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Subject

class MavenExtensionDownloadHandlerTest extends Specification {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder()

    @Subject
    private final MavenExtensionDownloadHandler mavenExtensionDownloadHandler = new MavenExtensionDownloadHandler()

    def 'extensions are not redownloaded if config has not changed'() {
        given:
        def controllerFolder = tempFolder.newFolder()

        def originalConfig = Mock(InjectionConfig)
        with(originalConfig) {
            enabled >> true
            server >> 'https://scans.gradle.com'
            mavenExtensionVersion >> '1.22.1'
            ccudExtensionVersion >> '2.0.1'
        }

        when:
        def extensionsDownloaded = mavenExtensionDownloadHandler.ensureExtensionsDownloaded(controllerFolder, originalConfig)

        then:
        extensionsDownloaded.size() == 2
        def originalDevelocityDigest = extensionsDownloaded.get(MavenExtension.DEVELOCITY)
        def originalCcudDigest = extensionsDownloaded.get(MavenExtension.CCUD)

        originalDevelocityDigest != null && originalCcudDigest != null

        when:
        def sameExtensions = mavenExtensionDownloadHandler.ensureExtensionsDownloaded(controllerFolder, originalConfig)

        then:
        sameExtensions.size() == 2
        sameExtensions.get(MavenExtension.DEVELOCITY) == originalDevelocityDigest && sameExtensions.get(MavenExtension.CCUD) == originalCcudDigest
    }

    def 'configuration change triggers re-download of the extensions'() {
        given:
        def controllerFolder = tempFolder.newFolder()

        def originalConfig = Mock(InjectionConfig)
        with(originalConfig) {
            enabled >> true
            server >> 'https://scans.gradle.com'
            mavenExtensionVersion >> '1.22'
            ccudExtensionVersion >> '2.0'
        }

        when:
        def extensionsDownloaded = mavenExtensionDownloadHandler.ensureExtensionsDownloaded(controllerFolder, originalConfig)

        then:
        extensionsDownloaded.size() == 2
        def originalDevelocityDigest = extensionsDownloaded.get(MavenExtension.DEVELOCITY)
        def originalCcudDigest = extensionsDownloaded.get(MavenExtension.CCUD)

        originalDevelocityDigest != null && originalCcudDigest != null

        when:
        def updatedConfig = Mock(InjectionConfig)
        with(updatedConfig) {
            enabled >> true
            server >> 'https://scans.gradle.com'
            mavenExtensionVersion >> '1.22.1'
            ccudExtensionVersion >> '2.0.1'
        }

        def redownloadedExtensions = mavenExtensionDownloadHandler.ensureExtensionsDownloaded(controllerFolder, updatedConfig)

        then:
        redownloadedExtensions.size() == 2
        def redownloadedDevelocityDigest = redownloadedExtensions.get(MavenExtension.DEVELOCITY)
        def redownloadedCcudDigest = redownloadedExtensions.get(MavenExtension.CCUD)

        redownloadedDevelocityDigest != null && redownloadedCcudDigest != null

        and:
        redownloadedDevelocityDigest != originalDevelocityDigest && redownloadedCcudDigest != originalCcudDigest
    }

}
