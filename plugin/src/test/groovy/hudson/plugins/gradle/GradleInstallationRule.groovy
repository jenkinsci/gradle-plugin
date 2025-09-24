package hudson.plugins.gradle

import hudson.Functions
import hudson.model.DownloadService
import hudson.tools.InstallSourceProperty
import hudson.util.FormValidation
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.jvnet.hudson.test.JenkinsRule

import java.util.concurrent.TimeUnit

class GradleInstallationRule extends TestWatcher {

    static {
        System.setProperty('hudson.model.DownloadService.noSignatureCheck', 'true')
    }

    String gradleVersion
    private final JenkinsRule j

    GradleInstallationRule(String gradleVersion = '8.14.3', JenkinsRule j) {
        this.gradleVersion = gradleVersion
        this.j = j
    }

    @Override
    protected void starting(Description description) {
        loadGradleToolInstallers()
    }

    private void loadGradleToolInstallers() {
        assert j.jenkins.getExtensionList(DownloadService.Downloadable).find {
            it.id == GradleInstaller.name
        }.updateNow().kind == FormValidation.Kind.OK
    }

    void addInstallation() {
        addInstallations(gradleVersion)
    }

    void addInstallations(String... installationNames) {
        def installations = installationNames.collect { name ->
            new GradleInstallation(name, '', [new InstallSourceProperty([new GradleInstaller(gradleVersion)])])
        }

        addInstallations(*installations)
    }

    void addInstallations(GradleInstallation... installations) {
        def gradleInstallationDescriptor = j.jenkins.getDescriptorByType(GradleInstallation.DescriptorImpl)
        gradleInstallationDescriptor.setInstallations(installations)
        assert gradleInstallationDescriptor.getInstallations()
    }

    @Override
    protected void finished(Description description) {
        super.finished(description)
        if(Functions.isWindows()) {
            try {
                println 'Killing Gradle processes'
                def proc = """powershell.exe -NoProfile -Command Stop-Process -Id (Get-Process java | Where-Object { \$_.CommandLine -like "*GradleDaemon*" -and \$_.CommandLine -like "*${gradleVersion}*" }).Id -Force""".execute()
                proc.waitFor(30, TimeUnit.SECONDS)
                println "output: ${proc.text}"
                println "code: ${proc.exitValue()}"
            } catch (Exception e) {
                System.err.println('Failed killing Gradle daemons')
                e.printStackTrace()
            }
        }
    }
}
