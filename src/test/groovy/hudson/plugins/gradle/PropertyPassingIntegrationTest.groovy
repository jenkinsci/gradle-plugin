package hudson.plugins.gradle

import hudson.model.FreeStyleProject
import hudson.remoting.Launcher
import org.jvnet.hudson.test.CreateFileBuilder
import spock.lang.Unroll

import static org.jvnet.hudson.test.JenkinsRule.getLog

@Unroll
class PropertyPassingIntegrationTest extends GradleAbstractIntegrationTest {

    private static final Map<String, String> CRITICAL_PROPERTIES = [
        property1: 'a < b',
        property2: '<foo> <bar/> </foo>',
        property3: 'renaming XYZ >> \'xyz\'',
        property4: 'renaming XYZ >>> \'xyz\'',
        property5: 'renaming XYZ >> "xyz"',
        property6: 'renaming \'XYZ >> \'x"y"z\'"'
    ]

    def "pass '#escapedPropertyValue' via parameter in system properties"() {
        given:
        gradleInstallationRule.addInstallation()
        def p = j.createFreeStyleProject()
        addParameter(p, 'PARAM')
        createBuildScript(p, """
            task printParam {
                doLast {
                    println 'property=' + System.getProperty('PARAM')
                }
            }""".stripIndent())
        p.buildersList.add(new Gradle(tasks: 'printParam', useWorkspaceAsHome: true, passAllAsSystemProperties: true, *: defaults))

        when:
        def build = j.assertBuildStatusSuccess(triggerBuildWithParameter(p, "PARAM", propertyValue))

        then:
        getLog(build).contains("property=${propertyValue}")

        where:
        propertyValue << criticalStrings
        escapedPropertyValue = escapeStringForMethodName(propertyValue)
    }

    def "pass '#escapedPropertyValue' via parameter in project properties"() {
        given:
        gradleInstallationRule.addInstallation()
        def p = j.createFreeStyleProject()
        addParameter(p, "PARAM")
        createBuildScript(p, """
            task printParam {
                doLast {
                    println 'property=' + PARAM
                }
            }""".stripIndent())
        p.buildersList.add(new Gradle(tasks: 'printParam', useWorkspaceAsHome: true, passAllAsProjectProperties: true, *: defaults))

        when:
        def build = j.assertBuildStatusSuccess(triggerBuildWithParameter(p, "PARAM", propertyValue))

        then:
        getLog(build).contains("property=${propertyValue}")

        where:
        propertyValue << criticalStrings
        escapedPropertyValue = escapeStringForMethodName(propertyValue)
    }

    def "pass project properties"() {
        given:
        gradleInstallationRule.addInstallation()
        def p = j.createFreeStyleProject()
        createBuildScript(p, """
            task printParam {
                doLast {
                ${CRITICAL_PROPERTIES.collect { k, v ->
                    "println('${k}=' + ${k})"
                }.join('\n')}
                }
            }""".stripIndent())
        p.buildersList.add(new Gradle(tasks: 'printParam', useWorkspaceAsHome: true, projectProperties: map2PropertiesString(CRITICAL_PROPERTIES), *: defaults))

        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        CRITICAL_PROPERTIES.each { key, value ->
            assert getLog(build).contains("${key}=${value}")
        }
    }

    def "pass system properties"() {
        given:
        gradleInstallationRule.addInstallation()
        def p = j.createFreeStyleProject()
        createBuildScript(p, """
            task printParam {
                doLast {
                ${CRITICAL_PROPERTIES.collect { k, v ->
                    "println('${k}=' + System.getProperty('${k}'))"
                }.join('\n')}
                }
            }""".stripIndent())
        p.buildersList.add(new Gradle(tasks: 'printParam', useWorkspaceAsHome: true, systemProperties: map2PropertiesString(CRITICAL_PROPERTIES), *: defaults))

        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        CRITICAL_PROPERTIES.each { key, value ->
            assert getLog(build).contains("${key}=${value}")
        }
    }

    private static List<String> getCriticalStrings() {
        return CRITICAL_PROPERTIES.values() + [
            Launcher.isWindows() ? "Multiline does not work on windows \\r\\n" : """
                   Some
                   multiline
                   parameter""".stripIndent().replaceAll('\n', System.lineSeparator())
        ]
    }

    private static boolean createBuildScript(FreeStyleProject p, String buildScript) {
        p.buildersList.add(new CreateFileBuilder("build.gradle", buildScript))
    }

    private static String escapeStringForMethodName(value) {
        value.replaceAll('\r\n', '\\\\r\\\\n').replaceAll('\n', '\\\\n')
    }

    private static String map2PropertiesString(Map<String, String> properties) {
        (properties.collect { k, v -> "${k}=${v}\n" }).join('')
    }
}
