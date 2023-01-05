package hudson.plugins.gradle.injection

import hudson.EnvVars
import hudson.Util
import hudson.model.FreeStyleProject
import hudson.model.Result
import hudson.model.Slave
import hudson.plugins.gradle.Gradle
import hudson.slaves.DumbSlave
import hudson.slaves.EnvironmentVariablesNodeProperty
import hudson.util.Secret
import org.apache.commons.lang3.StringUtils
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jvnet.hudson.test.CreateFileBuilder
import spock.lang.Unroll

@Unroll
class BuildScanInjectionGradleIntegrationTest extends BaseGradleInjectionIntegrationTest {

    private static final String GRADLE_ENTERPRISE_PLUGIN_VERSION = '3.11.1'
    private static final String CCUD_PLUGIN_VERSION = '1.8.1'

    private static final String MSG_INIT_SCRIPT_APPLIED = "Connection to Gradle Enterprise: http://foo.com"

    private static final List<String> GRADLE_VERSIONS = ['4.10.3', '5.6.4', '6.9.2', '7.5.1']

    private static final EnvVars EMPTY_ENV = new EnvVars()

    def 'skips injection if the agent is offline'() {
        given:
        def gradleVersion = '7.5.1'

        def agent = createSlave("test")

        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        withGlobalEnvVars {
            put("JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_GRADLE_HOME", getGradleHome(agent, gradleVersion))
        }

        restartSlave(agent)

        when:
        def webClient = j.createWebClient()
        def page = webClient.goTo("configure")
        def form = page.getFormByName("config")

        form.getInputByName("_.injectionEnabled").click()
        form.getInputByName("_.server").setValueAttribute("https://localhost")
        form.getInputByName("_.gradlePluginVersion").setValueAttribute("3.11.1")

        j.submit(form)

        then:
        with(agentEnvVars(agent)) {
            get("JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION") == "3.11.1"
        }

        when:
        page = webClient.goTo("configure")
        form = page.getFormByName("config")

        form.getInputByName("_.gradlePluginVersion").setValueAttribute("")

        j.submit(form)

        then:
        with(agentEnvVars(agent)) {
            get("JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION") == null
        }

        when:
        j.disconnectSlave(agent)

        page = webClient.goTo("configure")
        form = page.getFormByName("config")

        form.getInputByName("_.gradlePluginVersion").setValueAttribute("3.11.1")

        j.submit(form)

        then:
        with(agentEnvVars(agent)) {
            get("JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION") == null
        }
    }

    def 'uses custom plugin repository'() {
        given:
        // Gradle 7.x requires allowInsecureProtocol
        def gradleVersion = '6.9.2'
        def pluginRepositoryUrl = URI.create("https://plugins.gradle.org/m2/")

        def proxy = new ExternalRepoProxy(pluginRepositoryUrl)
        def proxyAddress = proxy.address

        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave agent = createSlave()

        FreeStyleProject project = j.createFreeStyleProject()
        project.setAssignedNode(agent)

        project.buildersList.add(buildScriptBuilder())
        project.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "--no-daemon"))

        when:
        // first build to download Gradle
        def firstRun = j.buildAndAssertSuccess(project)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, firstRun)

        when:
        enableBuildInjection(agent, gradleVersion, proxyAddress)
        def secondRun = j.buildAndAssertSuccess(project)

        then:
        j.assertLogContains(MSG_INIT_SCRIPT_APPLIED, secondRun)
        j.assertLogContains(
            "Gradle Enterprise plugins resolution: ${StringUtils.removeEnd(proxyAddress.toString(), "/")}", secondRun)

        cleanup:
        proxy.close()
    }

    def 'skips the injection if GE url is not set'(String gradleVersion) {
        given:
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave slave = createSlave(false)

        FreeStyleProject p = j.createFreeStyleProject()
        p.setAssignedNode(slave)

        p.buildersList.add(buildScriptBuilder())
        p.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "--no-daemon"))

        when:
        // first build to download Gradle
        def build = j.buildAndAssertSuccess(p)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, build)

        when:
        enableBuildInjection(slave, gradleVersion)

        then:
        !initScriptFile(slave, gradleVersion).exists()

        when:
        def build2 = j.buildAndAssertSuccess(p)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, build2)

        where:
        gradleVersion << GRADLE_VERSIONS
    }

    def 'Gradle #gradleVersion - freestyle - conditional build scan publication'(String gradleVersion) {
        given:
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave slave = createSlave()

        FreeStyleProject p = j.createFreeStyleProject()
        p.setAssignedNode(slave)

        p.buildersList.add(buildScriptBuilder())
        p.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "--no-daemon"))

        when:
        // first build to download Gradle
        def build = j.buildAndAssertSuccess(p)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, build)

        when:
        enableBuildInjection(slave, gradleVersion)
        def build2 = j.buildAndAssertSuccess(p)

        then:
        j.assertLogContains(MSG_INIT_SCRIPT_APPLIED, build2)

        where:
        gradleVersion << GRADLE_VERSIONS
    }

    def 'Gradle #gradleVersion - pipeline - conditional build scan publication'(String gradleVersion) {
        given:
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave slave = createSlave()

        def pipelineJob = j.createProject(WorkflowJob)

        pipelineJob.setDefinition(new CpsFlowDefinition("""
    stage('Build') {
      node('foo') {
        withGradle {
          def gradleHome = tool name: '${gradleInstallationRule.gradleVersion}', type: 'gradle'
          writeFile file: 'settings.gradle', text: ''
          writeFile file: 'build.gradle', text: ""
          if (isUnix()) {
            sh "'\${gradleHome}/bin/gradle' help --no-daemon --console=plain"
          } else {
            bat(/"\${gradleHome}\\bin\\gradle.bat" help --no-daemon --console=plain/)
          }
        }
      }
    }
""", false))

        when:
        // first build to download Gradle
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, build)

        when:
        enableBuildInjection(slave, gradleVersion)
        def build2 = j.buildAndAssertSuccess(pipelineJob)

        then:
        j.assertLogContains(MSG_INIT_SCRIPT_APPLIED, build2)

        where:
        gradleVersion << GRADLE_VERSIONS
    }

    def 'Gradle #gradleVersion - init script is deleted when auto-injection is turned off'(String gradleVersion) {
        given:
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave slave = createSlave()

        File initScript = initScriptFile(slave, gradleVersion)

        expect:
        !initScript.exists()

        when:
        enableBuildInjection(slave, gradleVersion)

        then:
        initScript.exists()

        when:
        disableBuildInjection(slave)

        then:
        !initScript.exists()

        where:
        gradleVersion << GRADLE_VERSIONS
    }

    def 'Gradle #gradleVersion - init script is deleted without gradle plugin version set'(String gradleVersion) {
        given:
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave slave = createSlave()

        File initScript = initScriptFile(slave, gradleVersion)

        expect:
        !initScript.exists()

        when:
        enableBuildInjection(slave, gradleVersion, null, true)

        then:
        initScript.exists()

        when:
        disableBuildInjection(slave)

        then:
        initScript.exists()

        when:
        turnOffBuildInjection(slave)

        then:
        !initScript.exists()

        where:
        gradleVersion << GRADLE_VERSIONS
    }

    def 'injection is enabled and disabled based on node labels'(String gradleVersion) {
        given:
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave slave = createSlave()

        File initScript = initScriptFile(slave, gradleVersion)

        expect:
        !initScript.exists()

        when:
        enableBuildInjection(slave, gradleVersion)

        then:
        initScript.exists()

        when:
        withInjectionConfig {
            gradleInjectionDisabledNodes = labels('bar', 'foo')
        }
        restartSlave(slave)

        then:
        !initScript.exists()

        when:
        withInjectionConfig {
            gradleInjectionDisabledNodes = null
            gradleInjectionEnabledNodes = labels('daz', 'foo')
        }
        restartSlave(slave)

        then:
        initScript.exists()

        when:
        withInjectionConfig {
            gradleInjectionDisabledNodes = null
            gradleInjectionEnabledNodes = labels('daz')
        }
        restartSlave(slave)

        then:
        !initScript.exists()

        where:
        gradleVersion << GRADLE_VERSIONS
    }

    def "doesn't copy init script if already exists"() {
        given:
        def gradleVersion = '7.5.1'

        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave agent = createSlave()

        def initScript = initScriptFile(agent, gradleVersion)

        when:
        enableBuildInjection(agent, gradleVersion, URI.create("https://my-company.com/m2/"))

        then:
        initScript.exists()
        def firstLastModified = initScript.lastModified()
        firstLastModified > 0

        when:
        enableBuildInjection(agent, gradleVersion)

        then:
        initScript.exists()
        def secondLastModified = initScript.lastModified()
        firstLastModified == secondLastModified
    }

    def "copies init script if it was changed"() {
        given:
        def gradleVersion = '7.5.1'

        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave agent = createSlave()

        def initScript = initScriptFile(agent, gradleVersion)

        when:
        enableBuildInjection(agent, gradleVersion, URI.create("https://my-company.com/m2/"))

        then:
        initScript.exists()
        def firstDigest = Util.getDigestOf(initScript)
        firstDigest != null

        when:
        initScript << "\n// comment"

        then:
        def secondDigest = Util.getDigestOf(initScript)
        secondDigest != firstDigest

        when:
        enableBuildInjection(agent, gradleVersion)

        then:
        initScript.exists()
        def thirdDigest = Util.getDigestOf(initScript)
        thirdDigest != secondDigest
        thirdDigest == firstDigest
    }

    def "sets all mandatory environment variables"() {
        given:
        def gradleVersion = '7.5.1'

        def agent = createSlave("test")

        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        withGlobalEnvVars {
            put("JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_GRADLE_HOME", getGradleHome(agent, gradleVersion))
        }

        when:
        withInjectionConfig {
            injectionEnabled = true
            server = "http://localhost"
            gradlePluginVersion = GRADLE_ENTERPRISE_PLUGIN_VERSION
        }

        restartSlave(agent)

        then:
        initScriptFile(agent, gradleVersion).exists()

        with(agent.getNodeProperty(EnvironmentVariablesNodeProperty.class)) {
            with(getEnvVars()) {
                get("JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL") == "http://localhost"
                get("JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION") == '3.11.1'
                get("JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER") == null
                get("JENKINSGRADLEPLUGIN_GRADLE_PLUGIN_REPOSITORY_URL") == null
                get("JENKINSGRADLEPLUGIN_CCUD_PLUGIN_VERSION") == null
            }
        }

        when:
        withInjectionConfig {
            injectionEnabled = true
            server = null
            gradlePluginVersion = null
        }

        restartSlave(agent)

        then:
        !initScriptFile(agent, gradleVersion).exists()

        with(agent.getNodeProperty(EnvironmentVariablesNodeProperty.class)) {
            with(getEnvVars()) {
                get("JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL") == null
                get("JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION") == null
                get("JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER") == null
                get("JENKINSGRADLEPLUGIN_GRADLE_PLUGIN_REPOSITORY_URL") == null
                get("JENKINSGRADLEPLUGIN_CCUD_PLUGIN_VERSION") == null
            }
        }
    }

    def "sets all optional environment variables"() {
        given:
        def gradleVersion = '7.5.1'

        def agent = createSlave("test")

        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        withGlobalEnvVars {
            put("JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_GRADLE_HOME", getGradleHome(agent, gradleVersion))
        }

        when:
        withInjectionConfig {
            injectionEnabled = true
            server = 'http://localhost'
            allowUntrusted = true
            gradlePluginVersion = GRADLE_ENTERPRISE_PLUGIN_VERSION
            ccudPluginVersion = CCUD_PLUGIN_VERSION
            gradlePluginRepositoryUrl = 'http://localhost/repository'
        }

        restartSlave(agent)

        then:
        initScriptFile(agent, gradleVersion).exists()

        with(agent.getNodeProperty(EnvironmentVariablesNodeProperty.class)) {
            with(getEnvVars()) {
                get("JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL") == "http://localhost"
                get("JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION") == '3.11.1'
                get("JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER") == "true"
                get("JENKINSGRADLEPLUGIN_GRADLE_PLUGIN_REPOSITORY_URL") == "http://localhost/repository"
                get("JENKINSGRADLEPLUGIN_CCUD_PLUGIN_VERSION") == "1.8.1"
            }
        }

        when:
        withInjectionConfig {
            injectionEnabled = true
            server = null
            allowUntrusted = false
            gradlePluginVersion = null
            ccudPluginVersion = null
            gradlePluginRepositoryUrl = null
        }

        restartSlave(agent)

        then:
        !initScriptFile(agent, gradleVersion).exists()

        with(agent.getNodeProperty(EnvironmentVariablesNodeProperty.class)) {
            with(getEnvVars()) {
                get("JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL") == null
                get("JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION") == null
                get("JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER") == null
                get("JENKINSGRADLEPLUGIN_GRADLE_PLUGIN_REPOSITORY_URL") == null
                get("JENKINSGRADLEPLUGIN_CCUD_PLUGIN_VERSION") == null
            }
        }
    }

    def "access key is injected into the build"() {
        def gradleVersion = '7.5.1'

        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave agent = createSlave()

        FreeStyleProject project = j.createFreeStyleProject()
        project.setAssignedNode(agent)

        project.buildersList.add(buildScriptBuilder())
        project.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "--no-daemon"))

        when:
        // first build to download Gradle
        def firstRun = j.buildAndAssertSuccess(project)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, firstRun)

        when:
        enableBuildInjection(agent, gradleVersion)
        withInjectionConfig {
            accessKey = Secret.fromString("invalid")
        }
        def secondRun = j.buildAndAssertStatus(Result.FAILURE, project)

        then:
        j.assertLogContains(MSG_INIT_SCRIPT_APPLIED, secondRun)
        j.assertLogContains("Failed to parse GRADLE_ENTERPRISE_ACCESS_KEY environment variable", secondRun)
    }

    private static CreateFileBuilder buildScriptBuilder() {
        return new CreateFileBuilder('build.gradle', """
task hello {
  doLast {
    println 'Hello!'
  }
}
""")
    }

    private DumbSlave createSlave(boolean setGeUrl = true) {
        withInjectionConfig {
            ccudPluginVersion = CCUD_PLUGIN_VERSION
            server = setGeUrl ? 'http://foo.com' : null
        }

        return createSlave('foo')
    }

    private static File initScriptFile(DumbSlave agent, String gradleVersion) {
        return new File("${getGradleHome(agent, gradleVersion)}/init.d/init-build-scan.gradle")
    }

    private static String getGradleHome(DumbSlave slave, String gradleVersion) {
        return "${slave.getRemoteFS()}/tools/hudson.plugins.gradle.GradleInstallation/${gradleVersion}"
    }

    private void enableBuildInjection(DumbSlave slave,
                                      String gradleVersion,
                                      URI repositoryAddress = null,
                                      Boolean globalAutoInjectionCheckEnabled = false) {
        withGlobalEnvVars {
            put("JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_GRADLE_HOME", getGradleHome(slave, gradleVersion))
            put('GRADLE_OPTS', '-Dscan.uploadInBackground=false')
            if (globalAutoInjectionCheckEnabled) {
                put("JENKINSGRADLEPLUGIN_GLOBAL_AUTO_INJECTION_CHECK", "true")
            }
        }

        withInjectionConfig {
            injectionEnabled = true
            gradlePluginVersion = GRADLE_ENTERPRISE_PLUGIN_VERSION
            gradlePluginRepositoryUrl = repositoryAddress?.toString()
        }

        restartSlave(slave)
    }

    private void disableBuildInjection(DumbSlave slave) {
        withInjectionConfig {
            injectionEnabled = false
        }

        restartSlave(slave)
    }

    private void turnOffBuildInjection(DumbSlave slave) {
        withInjectionConfig {
            injectionEnabled = true
            gradlePluginVersion = null
        }

        restartSlave(slave)
    }

    private static EnvVars agentEnvVars(Slave agent) {
        def nodeProperty = agent.getNodeProperties().get(EnvironmentVariablesNodeProperty.class)
        return nodeProperty != null ? nodeProperty.getEnvVars() : EMPTY_ENV
    }
}
