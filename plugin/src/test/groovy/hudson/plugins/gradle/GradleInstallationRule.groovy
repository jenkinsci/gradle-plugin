package hudson.plugins.gradle

import hudson.Functions
import hudson.model.DownloadService
import hudson.tools.InstallSourceProperty
import hudson.util.FormValidation
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.jvnet.hudson.test.JenkinsRule

import java.nio.file.Files
import java.nio.file.Paths
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
        if (Functions.isWindows()) {
            try {
                println 'Killing Gradle processes'
                Files.write(
                    Paths.get('kill-gradle-processes.ps1'),
                    '''
                      $procs = Get-CimInstance Win32_Process -Filter "Name='java.exe' AND CommandLine LIKE '%GradleDaemon%'"
                      if ($procs) {
                          foreach ($p in $procs) {
                              $res = Invoke-CimMethod -InputObject $p -MethodName Terminate
                              Write-Output ("Terminated {0} -> ReturnValue {1}" -f $p.ProcessId, $res.ReturnValue)
                          }
                      } else {
                          Write-Output "No GradleDaemon java processes found."
                      }
                      exit 0
                    '''.stripIndent().trim().getBytes()
                )
                def proc =
                    ['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'kill-gradle-processes.ps1']
                        .execute()
                proc.waitFor(30, TimeUnit.SECONDS)
                println "code: ${proc.exitValue()}"
                println "stdout output: ${proc.in.text}"
                println "stderr output: ${proc.err.text}"
            } catch (Exception e) {
                System.err.println('Failed killing Gradle daemons')
                e.printStackTrace()
            }
        }
    }
}
