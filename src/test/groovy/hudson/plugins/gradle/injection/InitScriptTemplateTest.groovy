package hudson.plugins.gradle.injection

import spock.lang.Specification

class InitScriptTemplateTest extends Specification {

    def "all placeholders are replaceable"() {
        when:
        def result = InitScriptTemplate.loadAndReplace(GradleBuildScanInjection.RESOURCE_INIT_SCRIPT_GRADLE)

        then:
        noExceptionThrown()

        and:
        InitScriptVariables.values().each {
            assert !result.contains("'${it.templateName}'")
            assert result.contains(it.templateTargetName)
        }
    }
}
