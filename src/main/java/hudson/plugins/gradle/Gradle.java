package hudson.plugins.gradle;

import hudson.tasks.Builder;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Project;
import hudson.model.Descriptor;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.CopyOnWrite;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormFieldValidator;

import java.io.IOException;
import java.io.File;
import java.util.Map;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;

/**
 * @author Gregory Boissinot - Zenika
 */
public class Gradle extends Builder {
	
	
    /**
     * The Gradle command line switches
     */
    private final String switches;

    /**
     * The Gradle tasks
     */
    private final String tasks;

        
    /**
     * The Gradle build file path
     */    
    private final String buildFile;
    
    /**
     * Identifies {@link GradleInstallation} to be used.
     */
    private final String gradleName;

    @DataBoundConstructor
    public Gradle(String switches, String tasks, String buildFile, String gradleName) {
        this.switches = switches;
        this.tasks=tasks;
        this.gradleName = gradleName;
        this.buildFile=buildFile;
    }
    

	public String getSwitches() {
		return switches;
	}

	public String getBuildFile() {
		return buildFile;
	}

	public String getGradleName() {
		return gradleName;
	}

	public String getTasks() {
		return tasks;
	}



	/**
     * Gets the Gradle to invoke,
     * or null to invoke the default one.
     */
    public GradleInstallation getGradle() {
        for( GradleInstallation i : DESCRIPTOR.getInstallations() ) {
            if(gradleName!=null && i.getName().equals(gradleName))
                return i;
        }
        return null;
    }

    public boolean perform(Build<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
        Project proj = build.getProject();

        ArgumentListBuilder args = new ArgumentListBuilder();

        String execName;
        if(launcher.isUnix())
            execName = "gradle";
        else
            execName = "gradle.bat";

        String normalizedSwitches = switches.replaceAll("[\t\r\n]+"," ");
        normalizedSwitches=Util.replaceMacro(normalizedSwitches, build.getEnvVars());

        String normalizedTasks = tasks.replaceAll("[\t\r\n]+"," ");
        
        
        GradleInstallation ai = getGradle();
        if(ai==null) {
            args.add(execName);
        } else {
            File exec = ai.getExecutable();
            if(!ai.getExists()) {
                listener.fatalError(exec+" doesn't exist");
                return false;
            }
            args.add(exec.getPath());
        }
        args.addKeyValuePairs("-D",build.getBuildVariables());
        args.addTokenized(normalizedSwitches);
        args.addTokenized(normalizedTasks);

        Map<String,String> env = build.getEnvVars();
        if(ai!=null)
            env.put("GRADLE_HOME",ai.getGradleHome());

        if(!launcher.isUnix()) {
            // on Windows, executing batch file can't return the correct error code,
            // so we need to wrap it into cmd.exe.
            // double %% is needed because we want ERRORLEVEL to be expanded after
            // batch file executed, not before. This alone shows how broken Windows is...
            args.prepend("cmd.exe","/C");
            args.add("&&","exit","%%ERRORLEVEL%%");
        }

        

        
        FilePath rootLauncher= null;
        if (buildFile!=null && buildFile.trim().length()!=0){
            String rootBuildScriptReal = Util.replaceMacro(buildFile, build.getEnvVars());
        	rootLauncher= new FilePath(proj.getModuleRoot(), new File(rootBuildScriptReal).getParent());
        }
        else{
        	rootLauncher=proj.getModuleRoot();
        }
        
        
        try {
            int r = launcher.launch(args.toCommandArray(),env,listener.getLogger(),rootLauncher).join();
            return r==0;
        } catch (IOException e) {
            Util.displayIOException(e,listener);
            e.printStackTrace( listener.fatalError("command execution failed") );
            return false;
        }
    }

    public Descriptor<Builder> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends Descriptor<Builder> {
    	
        @CopyOnWrite
        private volatile GradleInstallation[] installations = new GradleInstallation[0];

        private DescriptorImpl() {
            super(Gradle.class);
            load();
        }

        public String getHelpFile() {
            return "/plugin/gradle/help.html";
        }

        public String getDisplayName() {
            return "Invoke Gradle script";
        }

        public GradleInstallation[] getInstallations() {
            return installations;
        }

        public boolean configure(StaplerRequest req) {
            installations = req.bindParametersToList(GradleInstallation.class,"gradle.").toArray(new GradleInstallation[0]);
            save();
            return true;
        }
        
        /**
         * Checks if the specified Hudson GRADLE_HOME is valid.
         */
        public void doCheckGradleHome( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
            
        	
            new FormFieldValidator(req,rsp,true) {
                public void check() throws IOException, ServletException {
                    File f = getFileParameter("value");
                    
                    if(!f.isDirectory()) {
                        error(f+" is not a directory");
                        return;
                    }
                    
                    if(!new File(f,"bin").exists() && !new File(f,"lib").exists()) {
                        error(f+" doesn't look like a Gradle directory");
                        return;
                    }

                    if(!new File(f,"bin/gradle").exists()) {
                        error(f+" doesn't look like a Gradle directory");
                        return;
                    }

                    ok();
                }
            }.process();
        }
    }



}
