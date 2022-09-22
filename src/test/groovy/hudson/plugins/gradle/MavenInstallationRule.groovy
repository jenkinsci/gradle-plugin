package hudson.plugins.gradle

import hudson.model.DownloadService
import hudson.tasks.Maven
import hudson.tools.InstallSourceProperty
import hudson.util.FormValidation
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.jvnet.hudson.test.JenkinsRule

class MavenInstallationRule extends TestWatcher {

    static {
        System.setProperty('hudson.model.DownloadService.noSignatureCheck', 'true')
    }

    String mavenVersion
    private final JenkinsRule j

    MavenInstallationRule(String mavenVersion = '3.3.1', JenkinsRule j) {
        this.mavenVersion = mavenVersion
        this.j = j
    }

    @Override
    protected void starting(Description description) {
        loadMavenToolInstallers()
    }

    private void loadMavenToolInstallers() {
        assert j.jenkins.getExtensionList(DownloadService.Downloadable).find {
            it.id == "hudson.tasks.Maven.MavenInstaller"
        }.updateNow().kind == FormValidation.Kind.OK
    }

    void addInstallation() {
        addInstallations(mavenVersion)
    }

    void addInstallations(String... installationNames) {
        def installations = installationNames.collect { name ->
            new Maven.MavenInstallation(name, '', [new InstallSourceProperty([new Maven.MavenInstaller(mavenVersion)])])
        }

        addInstallations(*installations)
    }

    void addInstallations(Maven.MavenInstallation... installations) {
        def mavenInstallationDescriptor = j.jenkins.getDescriptorByType(Maven.MavenInstallation.DescriptorImpl)
        mavenInstallationDescriptor.setInstallations(installations)
        assert mavenInstallationDescriptor.getInstallations()
    }
}
