package hudson.plugins.gradle

import hudson.tools.InstallSourceProperty
import hudson.util.FormValidation
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.jvnet.hudson.test.JenkinsRule

class GradleInstallationRule extends TestWatcher {
    static {
        System.setProperty("hudson.model.DownloadService.noSignatureCheck", "true");
    }

    final String gradleVersion
    private JenkinsRule j

    GradleInstallationRule(String gradleVersion = '3.0', JenkinsRule j) {
        this.gradleVersion = gradleVersion
        this.j = j
    }

    @Override
    protected void starting(Description description) {
        loadGradleToolInstallers()
    }

    private void loadGradleToolInstallers() {
        assert j.jenkins.getExtensionList(hudson.model.DownloadService.Downloadable).find {
            it.id == GradleInstaller.name
        }.updateNow().kind == FormValidation.Kind.OK
    }

    void addInstallation() {
        def gradleInstallationDescriptor = j.jenkins.getDescriptorByType(hudson.plugins.gradle.GradleInstallation.DescriptorImpl)
        gradleInstallationDescriptor.setInstallations(
                new GradleInstallation(gradleVersion, "", [new InstallSourceProperty([new GradleInstaller(gradleVersion)])]))

        assert gradleInstallationDescriptor.getInstallations()
    }
}
