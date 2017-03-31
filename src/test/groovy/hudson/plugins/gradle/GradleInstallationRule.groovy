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

    String gradleVersion
    private JenkinsRule j

    GradleInstallationRule(String gradleVersion = '3.2.1', JenkinsRule j) {
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
        addInstallations(gradleVersion)
    }

    void addInstallations(String... installationNames) {
        def gradleInstallationDescriptor = j.jenkins.getDescriptorByType(hudson.plugins.gradle.GradleInstallation.DescriptorImpl)

        GradleInstallation[] installations = new GradleInstallation[installationNames.size()]

        for (int i = 0; i < installationNames.size(); i++) {
            String name = installationNames[i]
            installations[i] = new GradleInstallation(name, "", [new InstallSourceProperty([new GradleInstaller(gradleVersion)])])
        }

        gradleInstallationDescriptor.setInstallations(installations)

        assert gradleInstallationDescriptor.getInstallations()
    }
}
