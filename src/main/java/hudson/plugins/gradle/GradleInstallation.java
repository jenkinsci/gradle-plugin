package hudson.plugins.gradle;

import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;

/**
 * Gradle installation.
 * 
 * @author Gregory Boissinot
*/
public final class GradleInstallation {
    private final String name;
    private final String gradleHome;

    @DataBoundConstructor
    public GradleInstallation(String name, String home) {
        this.name = name;
        this.gradleHome = home;
    }

    /**
     * install directory.
     */
    public String getGradleHome() {
        return gradleHome;
    }

    /**
     * Human readable display name.
     */
    public String getName() {
        return name;
    }

    public File getExecutable() {
        String execName;
        if(File.separatorChar=='\\'){
            execName = "gradle.bat";
            //Must support the previous Gradle versions before the version 0.5 where the windows executable file is gradle.exe
            if (!new File(getGradleHome(),"bin/"+execName).exists()){
            	execName = "gradle.exe";
            }
        }
        else {
            execName = "gradle";
            
        }
        return new File(getGradleHome(),"bin/"+execName);
    }

    /**
     * Returns true if the executable exists.
     */
    public boolean getExists() {
        return getExecutable().exists();
    }
}
