package hudson.plugins.gradle

import hudson.model.Cause
import hudson.model.ParametersAction
import hudson.model.ParametersDefinitionProperty
import hudson.model.TextParameterDefinition
import hudson.model.TextParameterValue
import hudson.remoting.Launcher
import org.junit.Rule
import org.junit.rules.RuleChain
import org.jvnet.hudson.test.CreateFileBuilder
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Specification
import spock.lang.Unroll

import static org.jvnet.hudson.test.JenkinsRule.getLog

@Unroll
class PropertyPassingIntegrationTest extends Specification {
    private final JenkinsRule j = new JenkinsRule()
    private final GradleInstallationRule gradleInstallationRule = new GradleInstallationRule(j)
    @Rule
    public final RuleChain rules = RuleChain.outerRule(j).around(gradleInstallationRule)

    def "pass '#escapedPropertyValue' via parameter in system properties"() {
        given:
        gradleInstallationRule.addInstallation()
        def p = j.createFreeStyleProject()
        p.addProperty(new ParametersDefinitionProperty(new TextParameterDefinition('PARAM', null, null)))
        p.buildersList.add(new CreateFileBuilder("build.gradle", "task printParam { doLast { println 'property=' + System.getProperty('PARAM') } }"))
        p.buildersList.add(new Gradle(tasks: 'printParam', useWorkspaceAsHome: true, passAllAsSystemProperties: true, *:defaults))

        when:
        def build = j.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new TextParameterValue("PARAM", propertyValue))))

        then:
        getLog(build).contains("property=${propertyValue}")

        where:
        propertyValue << criticalStrings
        escapedPropertyValue=propertyValue.replaceAll('\r\n', '\\\\r\\\\n').replaceAll('\n', '\\\\n')
    }

    def "pass '#escapedPropertyValue' via parameter in project properties"() {
        given:
        gradleInstallationRule.addInstallation()
        def p = j.createFreeStyleProject()
        p.addProperty(new ParametersDefinitionProperty(new TextParameterDefinition('PARAM', null, null)))
        p.buildersList.add(new CreateFileBuilder("build.gradle", "task printParam { doLast { println 'property=' + PARAM } }"))
        p.buildersList.add(new Gradle(tasks: 'printParam', useWorkspaceAsHome: true, passAllAsProjectProperties: true, *:defaults))

        when:
        def build = j.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new TextParameterValue("PARAM", propertyValue))))

        then:
        getLog(build).contains("property=${propertyValue}")

        where:
        propertyValue << criticalStrings
        escapedPropertyValue=propertyValue.replaceAll('\r\n', '\\\\r\\\\n').replaceAll('\n', '\\\\n')
    }

    def "pass project properties"() {
        given:
        gradleInstallationRule.addInstallation()
        def p = j.createFreeStyleProject()
        p.buildersList.add(new CreateFileBuilder("build.gradle", "task printParam { doLast { \n ${criticalProperties.collect { k, v -> "println('${k}=' + ${k})" }.join('\n') } } }"))
        p.buildersList.add(new Gradle(tasks: 'printParam', useWorkspaceAsHome: true, projectProperties: (criticalProperties.collect { k, v -> "${k}=${v}\n"}).join(''), *:defaults))

        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        criticalProperties.each { key, value ->
            assert getLog(build).contains("${key}=${value}")
        }
    }

    def "pass system properties"() {
        given:
        gradleInstallationRule.addInstallation()
        def p = j.createFreeStyleProject()
        p.buildersList.add(new CreateFileBuilder("build.gradle", "task printParam { doLast { \n ${criticalProperties.collect { k, v -> "println('${k}=' + System.getProperty('${k}'))" }.join('\n') } } }"))
        p.buildersList.add(new Gradle(tasks: 'printParam', useWorkspaceAsHome: true, systemProperties: (criticalProperties.collect { k, v -> "${k}=${v}\n"}).join(''), *:defaults))

        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        criticalProperties.each { key, value ->
            assert getLog(build).contains("${key}=${value}")
        }
    }

    private static final Map<String, String> criticalProperties = [
            property1: 'a < b',
            property2: '<foo> <bar/> </foo>',
            property3: 'renaming XYZ >> \'xyz\'',
            property4: 'renaming XYZ >>> \'xyz\'',
            property5: 'renaming XYZ >> "xyz"',
            property6: 'renaming \'XYZ >> \'x"y"z\'"'
    ]

    private static List<String> getCriticalStrings() {
        return [
                'a < b',
                '<foo> <bar/> </foo>',
                'renaming XYZ >> \'xyz\'',
                'renaming XYZ >>> \'xyz\'',
                'renaming XYZ >> "xyz"',
                'renaming \'XYZ >> \'x"y"z\'"',
                Launcher.isWindows() ? "Multiline does not work on windows \\r\\n" : """
                   Some
                   multiline
                   parameter""".stripIndent().replaceAll('\n', System.lineSeparator())
        ]
    }

    Map getDefaults() {
        [gradleName: gradleInstallationRule.gradleVersion, useWorkspaceAsHome: true, switches: '--no-daemon']
    }
}
