package hudson.plugins.gradle.injection

import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import hudson.EnvVars
import hudson.Util
import hudson.model.FreeStyleProject
import hudson.model.Slave
import hudson.plugins.git.BranchSpec
import hudson.plugins.git.GitSCM
import hudson.plugins.gradle.BaseGradleIntegrationTest
import hudson.plugins.gradle.BuildScanAction
import hudson.plugins.gradle.Gradle
import hudson.plugins.timestamper.TimestampNote
import hudson.plugins.timestamper.TimestamperBuildWrapper
import hudson.slaves.DumbSlave
import hudson.slaves.EnvironmentVariablesNodeProperty
import hudson.util.Secret
import org.apache.commons.lang3.StringUtils
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jvnet.hudson.test.CreateFileBuilder
import org.jvnet.hudson.test.JenkinsRule
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import spock.lang.Requires
import spock.lang.Unroll

@Unroll
class BuildScanInjectionGradleIntegrationTest extends BaseGradleIntegrationTest {

    private static final String MSG_INIT_SCRIPT_APPLIED = "Connection to Develocity: http://foo.com"

    private static final List<String> GRADLE_VERSIONS = ['7.6.4', '8.9']

    private static final EnvVars EMPTY_ENV = new EnvVars()

    def "does not capture build agent errors if checking for errors is disabled"() {
        given:
        System.setProperty(TimestampNote.systemProperty, 'true')
        def gradleVersion = '8.6'

        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave agent = createSlave()

        FreeStyleProject p = j.createFreeStyleProject()
        p.setAssignedNode(agent)

        p.buildersList.add(settingsFile())
        p.buildersList.add(helloTask())
        p.buildersList.add(new Gradle(tasks: '-Dcom.gradle.scan.trigger-synthetic-error=true -Ddevelocity.scan.trigger-synthetic-error=true hello', gradleName: gradleVersion, switches: "--no-daemon"))
        p.getBuildWrappersList().add(new TimestamperBuildWrapper())

        when:
        def firstRun = j.buildAndAssertSuccess(p)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, firstRun)

        when:
        enableBuildInjection(agent, gradleVersion)
        withInjectionConfig {
            checkForBuildAgentErrors = false
        }
        def secondRun = buildAndAssertFailure(p)

        then:
        secondRun.getAction(BuildScanAction) == null

        cleanup:
        System.clearProperty(TimestampNote.systemProperty)
    }

    def "captures build agent errors if checking for errors is enabled"() {
        given:
        System.setProperty(TimestampNote.systemProperty, 'true')
        def gradleVersion = '8.6'

        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave agent = createSlave()

        FreeStyleProject p = j.createFreeStyleProject()
        p.setAssignedNode(agent)

        p.buildersList.add(settingsFile())
        p.buildersList.add(helloTask())
        p.buildersList.add(new Gradle(tasks: '-Dcom.gradle.scan.trigger-synthetic-error=true -Ddevelocity.scan.trigger-synthetic-error=true hello', gradleName: gradleVersion, switches: "--no-daemon"))
        p.getBuildWrappersList().add(new TimestamperBuildWrapper())

        when:
        // first build to download Gradle
        def firstRun = j.buildAndAssertSuccess(p)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, firstRun)

        when:
        enableBuildInjection(agent, gradleVersion)
        withInjectionConfig {
            checkForBuildAgentErrors = true
        }
        def secondRun = buildAndAssertFailure(p)

        then:
        with(secondRun.getAction(BuildScanAction)) {
            scanUrls.isEmpty()
            hasGradleErrors
            !hasMavenErrors
        }

        cleanup:
        System.clearProperty(TimestampNote.systemProperty)
    }

    def "captures build agent errors in pipeline build if checking for errors is enabled"() {
        given:
        def gradleVersion = '8.6'
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave agent = createSlave()

        def pipelineJob = GradleSnippets.pipelineJobWithError(j, gradleInstallationRule)

        when:
        // first build to download Gradle
        def firstRun = j.buildAndAssertSuccess(pipelineJob)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, firstRun)

        when:
        enableBuildInjection(agent, gradleVersion)
        withInjectionConfig {
            checkForBuildAgentErrors = true
        }
        def secondRun = buildAndAssertFailure(pipelineJob)

        then:
        with(secondRun.getAction(BuildScanAction)) {
            scanUrls.isEmpty()
            hasGradleErrors
            !hasMavenErrors
        }
    }

    def "credentials are always masked in logs"() {
        given:
        def secret = 'confidential'
        registerCredentials('my-creds', secret)

        def gradleVersion = '8.6'
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave agent = createSlave()
        def pipelineJob = GradleSnippets.pipelineJobWithCredentials(j)

        when:
        // first build to download Gradle
        def firstRun = j.buildAndAssertSuccess(pipelineJob)

        then:
        j.assertLogContains('password=****', firstRun)
        j.assertLogNotContains(secret, firstRun)

        when:
        enableBuildInjection(agent, gradleVersion)
        withInjectionConfig {
            checkForBuildAgentErrors = true
        }
        def secondRun = j.buildAndAssertSuccess(pipelineJob)

        then:
        j.assertLogContains('password=****', secondRun)
        j.assertLogNotContains(secret, secondRun)
    }

    def 'skips injection if the agent is offline'() {
        given:
        def gradleVersion = '8.6'

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

        form.getInputByName("_.enabled").click()
        form.getInputByName("_.server").setValueAttribute("https://localhost")
        form.getInputByName("_.gradlePluginVersion").setValueAttribute("3.12.6")

        j.submit(form)

        then:
        with(agentEnvVars(agent)) {
            get("DEVELOCITY_INJECTION_DEVELOCITY_PLUGIN_VERSION") == "3.12.6"
        }

        when:
        page = webClient.goTo("configure")
        form = page.getFormByName("config")

        form.getInputByName("_.gradlePluginVersion").setValueAttribute("")

        j.submit(form)

        then:
        with(agentEnvVars(agent)) {
            get("DEVELOCITY_PLUGIN_VERSION") == null
        }

        when:
        j.disconnectSlave(agent)

        page = webClient.goTo("configure")
        form = page.getFormByName("config")

        form.getInputByName("_.gradlePluginVersion").setValueAttribute("3.12.6")

        j.submit(form)

        then:
        with(agentEnvVars(agent)) {
            get("DEVELOCITY_PLUGIN_VERSION") == null
        }
    }

    @Requires(value = { hasJdk11() }, reason = "Gradle 6.x requires Java 11")
    def 'uses custom plugin repository'() {
        given:
        // Gradle 7.x requires allowInsecureProtocol
        def gradleVersion = '6.9.4'
        def pluginRepositoryUrl = URI.create("https://plugins.gradle.org/m2/")

        def proxy = new ExternalRepoProxy(pluginRepositoryUrl)
        def proxyAddress = proxy.address

        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave agent = createSlave()

        FreeStyleProject project = j.createFreeStyleProject()
        project.setAssignedNode(agent)

        project.buildersList.add(settingsFile())
        project.buildersList.add(helloTask())
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
            "Develocity plugins resolution: ${StringUtils.removeEnd(proxyAddress.toString(), "/")}", secondRun)

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

        p.buildersList.add(settingsFile())
        p.buildersList.add(helloTask())
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

        p.buildersList.add(settingsFile())
        p.buildersList.add(helloTask())
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
            bat(/"\${gradleHome}\\bin\\gradle.bat" --stop/)
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

        FreeStyleProject p = j.createFreeStyleProject()
        p.setAssignedNode(slave)

        p.buildersList.add(settingsFile())
        p.buildersList.add(helloTask())
        p.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "--no-daemon"))

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

        def firstBuild = j.buildAndAssertSuccess(p)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, firstBuild)
        !initScript.exists()

        when:
        withInjectionConfig {
            gradleInjectionDisabledNodes = null
            gradleInjectionEnabledNodes = labels('daz', 'foo')
        }
        restartSlave(slave)

        def secondBuild = j.buildAndAssertSuccess(p)

        then:
        j.assertLogContains(MSG_INIT_SCRIPT_APPLIED, secondBuild)
        initScript.exists()

        when:
        withInjectionConfig {
            gradleInjectionDisabledNodes = null
            gradleInjectionEnabledNodes = labels('daz')
        }
        restartSlave(slave)

        def thirdBuild = j.buildAndAssertSuccess(p)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, thirdBuild)
        !initScript.exists()

        where:
        gradleVersion << GRADLE_VERSIONS
    }

    def "doesn't copy init script if already exists"() {
        given:
        def gradleVersion = '8.6'

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
        def gradleVersion = '8.6'

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

    @SuppressWarnings("GStringExpressionWithinString")
    def "short lived token is injected into the build"() {
        given:
        def gradleVersion = '8.6'

        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave agent = createSlave()

        FreeStyleProject project = j.createFreeStyleProject()
        project.setAssignedNode(agent)

        project.buildersList.add(settingsFile())
        project.buildersList.add(helloTask('println "accessKey=${System.getenv(\'DEVELOCITY_ACCESS_KEY\')}"'))
        project.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "--no-daemon"))

        def mockDevelocity = GroovyEmbeddedApp.of {
            handlers {
                post("api/auth/token") {
                    response.status(200)
                    response.send('some-token')
                }
            }
        }

        when:
        // first build to download Gradle
        def firstRun = j.buildAndAssertSuccess(project)

        then:
        j.assertLogNotContains("Connection to Develocity: ${mockDevelocity.address}", firstRun)

        when:
        enableBuildInjection(agent, gradleVersion)
        def credentialId = UUID.randomUUID().toString()
        withInjectionConfig {
            server = mockDevelocity.address.toString()
            accessKeyCredentialId = credentialId
        }
        SystemCredentialsProvider.getInstance().getCredentials().add(
                new StringCredentialsImpl(
                        CredentialsScope.GLOBAL,
                        credentialId,
                        "Develocity Access Key",
                        Secret.fromString("localhost=secret")
                ))
        SystemCredentialsProvider.getInstance().save()
        def secondRun = j.buildAndAssertSuccess(project)

        then:
        j.assertLogContains("Connection to Develocity: ${mockDevelocity.address}", secondRun)
        j.assertLogContains("accessKey=localhost=some-token", secondRun)
        j.assertLogNotContains("accessKey=localhost=secret", secondRun)
        j.assertLogContains("The response from ${mockDevelocity.address}scans/publish/gradle/${DEVELOCITY_PLUGIN_VERSION}/token was not from Develocity.", secondRun)
        j.assertLogNotContains(INVALID_ACCESS_KEY_FORMAT_ERROR, secondRun)

    }

    def "invalid access key is not injected into the build"() {
        given:
        def gradleVersion = '8.6'

        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave agent = createSlave()

        FreeStyleProject project = j.createFreeStyleProject()
        project.setAssignedNode(agent)

        project.buildersList.add(settingsFile())
        project.buildersList.add(helloTask())
        project.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "--no-daemon"))

        when:
        // first build to download Gradle
        def firstRun = j.buildAndAssertSuccess(project)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, firstRun)

        when:
        enableBuildInjection(agent, gradleVersion)
        def credentialId =UUID.randomUUID().toString()
        withInjectionConfig {
            accessKeyCredentialId = credentialId
        }
        SystemCredentialsProvider.getInstance().getCredentials().add(
                new StringCredentialsImpl(
                        CredentialsScope.GLOBAL,
                        credentialId,
                        "Develocity Access Key",
                        Secret.fromString("secret")
                ))
        SystemCredentialsProvider.getInstance().save()
        def secondRun = j.buildAndAssertSuccess(project)

        then:
        j.assertLogContains(MSG_INIT_SCRIPT_APPLIED, secondRun)
        j.assertLogContains("The response from http://foo.com/scans/publish/gradle/${DEVELOCITY_PLUGIN_VERSION}/token was not from Develocity.", secondRun)

        and:
        StringUtils.countMatches(JenkinsRule.getLog(secondRun), INVALID_ACCESS_KEY_FORMAT_ERROR) == 1
    }

    def "sets all mandatory environment variables"() {
        given:
        def gradleVersion = '8.6'

        def agent = createSlave("test")

        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        withGlobalEnvVars {
            put("JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_GRADLE_HOME", getGradleHome(agent, gradleVersion))
        }

        when:
        withInjectionConfig {
            enabled = true
            server = "http://localhost"
            gradlePluginVersion = DEVELOCITY_PLUGIN_VERSION
        }

        restartSlave(agent)

        then:
        initScriptFile(agent, gradleVersion).exists()

        with(agent.getNodeProperty(EnvironmentVariablesNodeProperty.class)) {
            verifyAll(getEnvVars()) {
                get("DEVELOCITY_INJECTION_URL") == "http://localhost"
                get("DEVELOCITY_INJECTION_DEVELOCITY_PLUGIN_VERSION") == this.DEVELOCITY_PLUGIN_VERSION
                get("DEVELOCITY_INJECTION_ALLOW_UNTRUSTED_SERVER") == null
                get("DEVELOCITY_INJECTION_ENFORCE_URL") == null
                get("DEVELOCITY_INJECTION_PLUGIN_REPOSITORY_URL") == null
                get("DEVELOCITY_INJECTION_CCUD_PLUGIN_VERSION") == null
                get("DEVELOCITY_INJECTION_CAPTURE_FILE_FINGERPRINTS") == 'true'
            }
        }

        when:
        withInjectionConfig {
            enabled = true
            server = null
            gradlePluginVersion = null
        }

        restartSlave(agent)

        then:
        !initScriptFile(agent, gradleVersion).exists()

        with(agent.getNodeProperty(EnvironmentVariablesNodeProperty.class)) {
            verifyAll(getEnvVars()) {
                get("DEVELOCITY_INJECTION_URL") == null
                get("DEVELOCITY_INJECTION_DEVELOCITY_PLUGIN_VERSION") == null
                get("DEVELOCITY_INJECTION_ALLOW_UNTRUSTED_SERVER") == null
                get("DEVELOCITY_INJECTION_ENFORCE_URL") == null
                get("DEVELOCITY_INJECTION_PLUGIN_REPOSITORY_URL") == null
                get("DEVELOCITY_INJECTION_CCUD_PLUGIN_VERSION") == null
                get("DEVELOCITY_INJECTION_CAPTURE_FILE_FINGERPRINTS") == null
            }
        }
    }

    def "sets all optional environment variables"() {
        given:
        def gradleVersion = '8.6'

        def agent = createSlave("test")

        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        withGlobalEnvVars {
            put("JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_GRADLE_HOME", getGradleHome(agent, gradleVersion))
        }

        when:
        withInjectionConfig {
            enabled = true
            server = 'http://localhost'
            allowUntrusted = true
            enforceUrl = true
            gradlePluginVersion = DEVELOCITY_PLUGIN_VERSION
            ccudPluginVersion = CCUD_PLUGIN_VERSION
            gradlePluginRepositoryUrl = 'http://localhost/repository'
            gradleCaptureTaskInputFiles = true
        }

        restartSlave(agent)

        then:
        initScriptFile(agent, gradleVersion).exists()

        with(agent.getNodeProperty(EnvironmentVariablesNodeProperty.class)) {
            verifyAll(getEnvVars()) {
                get("DEVELOCITY_INJECTION_URL") == "http://localhost"
                get("DEVELOCITY_INJECTION_ENFORCE_URL") == "true"
                get("DEVELOCITY_INJECTION_DEVELOCITY_PLUGIN_VERSION") == this.DEVELOCITY_PLUGIN_VERSION
                get("DEVELOCITY_INJECTION_ALLOW_UNTRUSTED_SERVER") == "true"
                get("DEVELOCITY_INJECTION_PLUGIN_REPOSITORY_URL") == "http://localhost/repository"
                get("DEVELOCITY_INJECTION_CCUD_PLUGIN_VERSION") == "2.0"
                get("DEVELOCITY_INJECTION_CAPTURE_FILE_FINGERPRINTS") == "true"
            }
        }

        when:
        withInjectionConfig {
            enabled = true
            server = null
            allowUntrusted = false
            gradlePluginVersion = null
            ccudPluginVersion = null
            gradlePluginRepositoryUrl = null
            gradleCaptureTaskInputFiles = false
        }

        restartSlave(agent)

        then:
        !initScriptFile(agent, gradleVersion).exists()

        with(agent.getNodeProperty(EnvironmentVariablesNodeProperty.class)) {
            verifyAll(getEnvVars()) {
                get("DEVELOCITY_INJECTION_URL") == null
                get("DEVELOCITY_INJECTION_DEVELOCITY_PLUGIN_VERSION") == null
                get("DEVELOCITY_INJECTION_ALLOW_UNTRUSTED_SERVER") == null
                get("DEVELOCITY_INJECTION_PLUGIN_REPOSITORY_URL") == null
                get("DEVELOCITY_INJECTION_CCUD_PLUGIN_VERSION") == null
                get("DEVELOCITY_INJECTION_ALLOW_UNTRUSTED_SERVER") == null
                get("DEVELOCITY_INJECTION_CAPTURE_FILE_FINGERPRINTS") == null
            }
        }
    }

    def 'vcs repository pattern injection for freestyle remote project - #filter #shouldApplyAutoInjection'(String filter, boolean shouldApplyAutoInjection) {
        given:
        def gradleVersion = '8.6'
        gradleInstallationRule.gradleVersion = '8.6'
        gradleInstallationRule.addInstallation()

        DumbSlave slave = createSlave()

        FreeStyleProject p = j.createFreeStyleProject()
        p.setScm(new GitSCM(GitSCM.createRepoList("https://github.com/c00ler/simple-gradle-project", null), [new BranchSpec('main')], null, null, null))
        p.setAssignedNode(slave)

        p.buildersList.add(new Gradle(tasks: 'clean', gradleName: gradleVersion, switches: "--no-daemon"))

        when:
        // first build to download Gradle
        def build = j.buildAndAssertSuccess(p)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, build)

        when:
        withInjectionConfig {
            vcsRepositoryFilter = filter
        }
        enableBuildInjection(slave, gradleVersion)
        def build2 = j.buildAndAssertSuccess(p)

        then:
        if (shouldApplyAutoInjection) {
            j.assertLogContains(MSG_INIT_SCRIPT_APPLIED, build2)
        } else {
            j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, build2)
        }

        where:
        filter                      | shouldApplyAutoInjection
        "+:simple-"                 | true
        "+:this-one-does-not-match" | false
    }

    def 'vcs repository pattern injection for pipeline remote project - #filter #shouldApplyAutoInjection'(String filter, boolean shouldApplyAutoInjection) {
        given:
        def gradleVersion = '8.6'
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave slave = createSlave()

        def pipelineJob = j.createProject(WorkflowJob)

        pipelineJob.setDefinition(new CpsFlowDefinition("""
    stage('Build') {
      node('foo') {
        withGradle {
          git branch: 'main', url: 'https://github.com/c00ler/simple-gradle-project'
          def gradleHome = tool name: '${gradleInstallationRule.gradleVersion}', type: 'gradle'
          if (isUnix()) {
            sh "'\${gradleHome}/bin/gradle' help --no-daemon --console=plain"
          } else {
            bat(/"\${gradleHome}\\bin\\gradle.bat" help --no-daemon --console=plain/)
            bat(/"\${gradleHome}\\bin\\gradle.bat" --stop/)
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
        withInjectionConfig {
            vcsRepositoryFilter = filter
        }
        enableBuildInjection(slave, gradleVersion)
        def build2 = j.buildAndAssertSuccess(pipelineJob)

        then:
        if (shouldApplyAutoInjection) {
            j.assertLogContains(MSG_INIT_SCRIPT_APPLIED, build2)
        } else {
            j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, build2)
        }

        where:
        filter                      | shouldApplyAutoInjection
        "+:simple-"                 | true
        "+:this-one-does-not-match" | false
    }

    def "logs injection messages with default Gradle log level"(boolean quiet) {
        given:
        def gradleVersion = '8.1.1'
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave agent = createSlave()

        FreeStyleProject project = j.createFreeStyleProject()
        project.setAssignedNode(agent)

        project.buildersList.add(settingsFile())
        project.buildersList.add(helloTask())
        project.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "--no-daemon${quiet ? ' -q' : ''}"))

        when:
        // first build to download Gradle
        def firstRun = j.buildAndAssertSuccess(project)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, firstRun)

        when:
        enableBuildInjection(agent, gradleVersion)
        withInjectionConfig {
            ccudPluginVersion = CCUD_PLUGIN_VERSION
        }
        def secondRun = j.buildAndAssertSuccess(project)

        then:
        def log = JenkinsRule.getLog(secondRun)
        assert quiet == !log.contains('Develocity plugins resolution: https://plugins.gradle.org/m2')
        assert quiet == !(log =~ /Applying com.gradle.develocity.agent.gradle.DevelocityPlugin with version (.*) via init script/)
        assert quiet == !log.contains('Connection to Develocity: http://foo.com, allowUntrustedServer: false, captureFileFingerprints: true')
        assert quiet == !(log =~ /Applying com.gradle.CommonCustomUserDataGradlePlugin with version (.*) via init script/)

        where:
        quiet << [true, false]
    }

    def 'capture task input files toggle is processed by init script'(String gradleVersion) {
        given:
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave agent = createSlave()

        FreeStyleProject project = j.createFreeStyleProject()
        project.setAssignedNode(agent)

        project.buildersList.add(settingsFile())
        project.buildersList.add(helloTask())
        project.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion))

        when:
        // first build to download Gradle
        def firstRun = j.buildAndAssertSuccess(project)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, firstRun)

        when:
        enableBuildInjection(agent, gradleVersion, null, false, true)
        def secondRun = j.buildAndAssertSuccess(project)

        then:
        def log = JenkinsRule.getLog(secondRun)
        log.contains('Connection to Develocity: http://foo.com, allowUntrustedServer: false')
        if (gradleVersion > '5.0') {
            assert log.contains('Setting captureFileFingerprints: true')
        }

        where:
        gradleVersion << GRADLE_VERSIONS
    }

    private static CreateFileBuilder helloTask(String action = "println 'Hello!'") {
        return new CreateFileBuilder('build.gradle', """
task hello {
  doLast {
    $action
  }
}
""")
    }

    private DumbSlave createSlave(boolean setGeUrl = true) {
        withInjectionConfig {
            server = setGeUrl ? 'http://foo.com' : null
        }

        return createSlave('foo')
    }

    private static File initScriptFile(DumbSlave agent, String gradleVersion) {
        return new File("${getGradleHome(agent, gradleVersion)}/init.d/init-build-scan.gradle")
    }

    private void disableBuildInjection(DumbSlave slave) {
        withInjectionConfig {
            enabled = false
        }

        restartSlave(slave)
    }

    private void turnOffBuildInjection(DumbSlave slave) {
        withInjectionConfig {
            enabled = true
            gradlePluginVersion = null
        }

        restartSlave(slave)
    }

    private static EnvVars agentEnvVars(Slave agent) {
        def nodeProperty = agent.getNodeProperties().get(EnvironmentVariablesNodeProperty.class)
        return nodeProperty != null ? nodeProperty.getEnvVars() : EMPTY_ENV
    }
}
