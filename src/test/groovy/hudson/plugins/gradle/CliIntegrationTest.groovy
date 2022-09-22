package hudson.plugins.gradle

import hudson.cli.CLICommandInvoker
import net.sf.json.JSON
import net.sf.json.JSONArray
import net.sf.json.JSONObject

class CliIntegrationTest extends GradleAbstractIntegrationTest {
    def 'list installations through CLI'() {
        when:
        CLICommandInvoker.Result result = new CLICommandInvoker(j, 'get-gradle').invoke()

        then:
        assertCLIResult(result, '{}')

        when:
        gradleInstallationRule.addInstallations('inst1')
        result = new CLICommandInvoker(j, 'get-gradle').invoke()

        then:
        assertCLIResult(result, expectedOutputForVersion('{"inst1":["%s"]}'))

        when:
        gradleInstallationRule.addInstallations('inst1', 'inst2')
        result = new CLICommandInvoker(j, 'get-gradle').invoke()

        then:
        assertCLIResult(result, expectedOutputForVersion('{"inst1":["%s"],"inst2":["%s"]}'))

        when:
        result = new CLICommandInvoker(j, 'get-gradle').invokeWithArgs('--name=inst1')

        then:
        assertCLIResult(result, expectedOutputForVersion('["%s"]'))

        when:
        result = new CLICommandInvoker(j, 'get-gradle').invokeWithArgs('--name=unknown')

        then:
        assertCLIError(result, 'Requested gradle installation not found: unknown')
    }

    private static void assertCLIResult(CLICommandInvoker.Result result, String expectedOutput) {
        assert result.returnCode() == 0

        JSON expectedJson, resultJson

        if (expectedOutput.startsWith('[')) {
            expectedJson = JSONArray.fromObject(expectedOutput)
            resultJson = JSONArray.fromObject(result.stdout().trim())
        } else {
            expectedJson = JSONObject.fromObject(expectedOutput)
            resultJson = JSONObject.fromObject(result.stdout().trim())
        }

        assert resultJson == expectedJson
    }

    private static void assertCLIError(CLICommandInvoker.Result result, String expectedOutput) {
        assert result.returnCode() == 1
        assert result.stderr().trim() == expectedOutput
    }

    private String expectedOutputForVersion(String output) {
        return String.format(output, gradleInstallationRule.gradleVersion, gradleInstallationRule.gradleVersion)
    }
}
