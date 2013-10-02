package hudson.plugins.gradle;

import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.InstallSourceProperty;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import hudson.tools.ToolPropertyDescriptor;
import hudson.util.DescribableList;
import net.sf.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Extension
public class GetGradleCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return "List all available gradle installations";
    }

    @Override
    public String getName() {
        return "get-gradle";
    }

    /* Execute the CLI command, returning results to stdout.
     *
     * This lists a subset of the known information about all gradle
     * installations, formatted as JSON.  On the jenkins server, this
     * information is stored in the file hudson.plugins.gradle.Gradle.xml.
     * What we are outputting here is a map, where the keys are the names of
     * all of the gradle installations, and the values are an array of id's
     * for all of the installers that correspond to the given installation.
     */
    @Override
    protected int run() throws Exception {
        GradleInstallation[] installations =
            Hudson.getInstance().getDescriptorByType(GradleInstallation.DescriptorImpl.class).getInstallations();

        Map<String,List<String>> map = new HashMap<String,List<String>>();

        for (GradleInstallation installation: installations) {
            for (Map.Entry<ToolPropertyDescriptor,ToolProperty<?>> entry: installation.getProperties().toMap().entrySet()) {
                DescribableList<ToolInstaller,Descriptor<ToolInstaller>> installers = ((InstallSourceProperty)entry.getValue()).installers;
                List<String> list = new ArrayList<String>();
                for (ToolInstaller installer: installers) {
                    list.add(((DownloadFromUrlInstaller)installer).id);
                }
                map.put(installation.getName(), list);
            }
        }

        JSONObject jsonObject = JSONObject.fromObject(map);
        stdout.println(jsonObject);

        return 0;
    }
}
