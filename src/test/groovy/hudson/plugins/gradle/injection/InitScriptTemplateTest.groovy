package hudson.plugins.gradle.injection


import spock.lang.Specification

import java.util.regex.Matcher
import java.util.regex.Pattern

class InitScriptTemplateTest extends Specification {
    private static final Pattern INPUT_PARAM_PATTERN = Pattern.compile("getInputParam\\([\"']([^\"']+)[\"']\\)")

    def "all placeholders are known"() {
        given:
        Set<String> knownKeys = loadKnownKeys()
        def script = CopyUtil.readResource(GradleBuildScanInjection.RESOURCE_INIT_SCRIPT_GRADLE)

        expect:
        validateTemplate(script, knownKeys)
    }

    def loadKnownKeys() {
        Set<String> knownKeys = new HashSet<>()
        Arrays.stream(InitScriptVariables.values())
            .forEach{v -> knownKeys.add(v.getTemplateName())}
        return knownKeys
    }


    void validateTemplate(String template, Set<String> knownReplacements) {
        Matcher matcher = INPUT_PARAM_PATTERN.matcher(template)
        while (matcher.find()) {
            if (!knownReplacements.contains(matcher.group(1))) {
                throw new IllegalArgumentException("Placeholder not found: " + matcher.group(1))
            }
        }
    }
}
