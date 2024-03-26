package hudson.plugins.gradle.injection

import hudson.FilePath
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static hudson.plugins.gradle.injection.MavenExtensionsTest.generateExtensionsXml

class MavenExtensionsDetectorTest extends Specification {

    private static final String DV = 'com.gradle:develocity-maven-extension:1.0'
    private static final String GRADLE_ENTERPRISE = 'com.gradle:gradle-enterprise-maven-extension:1.0'
    private static final String CCUD = 'com.gradle:common-custom-user-data-maven-extension:1.2'

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder()

    def injectionConfig = Mock(InjectionConfig)

    def 'detect DV and CCUD extension'() {
        given:
        with(injectionConfig) {
            injectMavenExtension >> true
            mavenExtensionCustomCoordinates >> customCoordinates
            ccudExtensionCustomCoordinates >> ccudCustomCoordinates
        }
        def workspace = tempFolder.newFolder('workspace')
        final FileTreeBuilder dir = new FileTreeBuilder(workspace)
        dir {
            '.mvn' {
                'extensions.xml'(generateExtensionsXml(
                    *(extensions.collect({ MavenCoordinates.parseCoordinates(it) }))))
            }
        }

        when:
        def detected = MavenExtensionsDetector.detect(injectionConfig, new FilePath(workspace))

        then:
        detected == expected as Set

        where:
        customCoordinates | ccudCustomCoordinates | extensions                        | expected
        null              | null                  | [DV]                              | [MavenExtension.DEVELOCITY]
        null              | null                  | [GRADLE_ENTERPRISE]               | [MavenExtension.GRADLE_ENTERPRISE]
        null              | null                  | ['my:ext:1.0']                    | []
        'my:ext'          | null                  | ['my:ext:2.0']                    | [MavenExtension.DEVELOCITY]
        'my:ext:1.0'      | null                  | ['my:ext:1.0']                    | [MavenExtension.DEVELOCITY]
        'my:ext:2.0'      | null                  | ['my:ext:1.0']                    | [MavenExtension.DEVELOCITY]
        null              | null                  | [CCUD]                            | [MavenExtension.CCUD]
        null              | 'my:ext-ccud'         | ['my:ext-ccud:1.0']               | [MavenExtension.CCUD]
        null              | 'my:ext-ccud:1.0'     | ['my:ext-ccud:1.0']               | [MavenExtension.CCUD]
        null              | 'my:ext-ccud:2.0'     | ['my:ext-ccud:1.0']               | [MavenExtension.CCUD]
        null              | null                  | [DV, CCUD]                        | [MavenExtension.DEVELOCITY, MavenExtension.CCUD]
        'my:ext'          | 'my:ext-ccud'         | ['my:ext:1.0', 'my:ext-ccud:1.0'] | [MavenExtension.DEVELOCITY, MavenExtension.CCUD]
    }

    def 'do not detect DV and CCUD extension when injection is disabled'() {
        given:
        with(injectionConfig) {
            injectMavenExtension >> false
            mavenExtensionCustomCoordinates >> 'my:ext'
            ccudExtensionCustomCoordinates >> 'my:ext-ccud'
        }
        def workspace = tempFolder.newFolder('workspace')
        final FileTreeBuilder dir = new FileTreeBuilder(workspace)
        dir {
            '.mvn' {
                'extensions.xml'(
                    generateExtensionsXml(
                        MavenCoordinates.parseCoordinates('my:ext:1.0'),
                        MavenCoordinates.parseCoordinates('my:ext-ccud:1.0')))
            }
        }

        when:
        def detected = MavenExtensionsDetector.detect(injectionConfig, new FilePath(workspace))

        then:
        detected == [] as Set
    }

    def 'do not detect DV and CCUD extension when extensions file is not present'() {
        given:
        with(injectionConfig) {
            injectMavenExtension >> true
        }
        def workspace = tempFolder.newFolder('workspace')

        when:
        def detected = MavenExtensionsDetector.detect(injectionConfig, new FilePath(workspace))

        then:
        detected == [] as Set
    }
}
