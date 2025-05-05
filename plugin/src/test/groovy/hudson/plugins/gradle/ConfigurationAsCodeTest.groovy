package hudson.plugins.gradle

import hudson.tools.InstallSourceProperty
import io.jenkins.plugins.casc.ConfigurationAsCode
import org.yaml.snakeyaml.Yaml

class ConfigurationAsCodeTest extends BaseGradleIntegrationTest {

    def 'import configuration'() {
        given:
        def configurationUrl = getClass().getResource(getClass().getSimpleName() + '/configuration-as-code.yml').toString()

        when:
        ConfigurationAsCode.get().configure(configurationUrl)

        then:
        def installations = j.jenkins.getDescriptorByType(GradleInstallation.DescriptorImpl).getInstallations()

        installations[0].name == 'gradle-5.0'
        installations[0].home == '/opt/gradle/gradle-5.0'
        installations[0].properties == []

        installations[1].name == 'gradle-4.0'
        installations[1].home == null
        installations[1].properties[0].class == InstallSourceProperty
        installations[1].properties[0].installers[0].class == GradleInstaller
        installations[1].properties[0].installers[0].id == '4.0'
        installations[1].properties[0].installers.size() == 1
        installations[1].properties.size() == 1

        installations.size() == 2
    }

    def 'export configuration'() {
        given:
        gradleInstallationRule.addInstallations(
            new GradleInstallation('gradle-5.0', '/opt/gradle/gradle-5.0', null),
            new GradleInstallation('gradle-4.0', null, [new InstallSourceProperty([new GradleInstaller('4.0')])]),
        )

        when:
        def outputStream = new ByteArrayOutputStream()
        ConfigurationAsCode.get().export(outputStream)

        then:
        def inputStream = new ByteArrayInputStream(outputStream.toByteArray())
        def configuration = new Yaml().load(inputStream)

        configuration.tool.gradle == [
            installations: [
                [name: 'gradle-5.0', home: '/opt/gradle/gradle-5.0'],
                [name: 'gradle-4.0', properties: [[installSource: [installers: [[gradleInstaller: [id: '4.0']]]]]]],
            ]
        ]
    }
}
