package hudson.plugins.gradle;

import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.model.Hudson;
//import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import hudson.model.Descriptor;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.InstallSourceProperty;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import hudson.tools.ToolPropertyDescriptor;
import hudson.util.DescribableList;
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

    @Override
    protected int run() throws Exception {
        GradleInstallation[] installations = Hudson.getInstance().getDescriptorByType(GradleInstallation.DescriptorImpl.class).getInstallations();
        // stdout.println("There are " + installations.length + " gradle installations");

        //JSONArray jsonArray = JSONArray.fromObject(installations);
        //stdout.println(jsonArray);

        Map<String,List<String>> map = new HashMap<String,List<String>>();

        for (GradleInstallation installation: installations) {
            // stdout.println("name = " + installation.getName());
            // stdout.println("home = " + installation.getHome());
            // //stdout.println("gradleHome = " + installation.gradleHome);
            // stdout.println("properties follow:");
            // // Map.Entry<ToolPropertyDescriptor,ToolProperty<?>>
            // // Map.Entry<InstallSourceProperty$DescriptorImpl,InstallSourceProperty>
            for (Map.Entry<ToolPropertyDescriptor,ToolProperty<?>> entry: installation.getProperties().toMap().entrySet()) {
                // stdout.println(entry.getKey().getClass().getName() + " => " + entry.getValue().getClass().getName());
                // stdout.println(entry.getKey().getDisplayName() + " => " + entry.getValue().getDescriptor().getDisplayName());
                // stdout.println(entry.getKey().getId() + " => " + entry.getValue().getDescriptor().getId());
                DescribableList<ToolInstaller,Descriptor<ToolInstaller>> installers = ((InstallSourceProperty)entry.getValue()).installers;
                // stdout.println(installers.getClass().getName());
                // stdout.println(installers.toString());
                // stdout.println("there are " + installers.size() + " installers");
                List<String> list = new ArrayList<String>();
                for (ToolInstaller installer: installers) {
                    // //stdout.println(installer.id + " , " + installer.getLabel());
                    // stdout.println(((DownloadFromUrlInstaller)installer).id);
                    list.add(((DownloadFromUrlInstaller)installer).id);
                    // stdout.println(installer.getLabel());
                }
                map.put(installation.getName(), list);
            }
            // stdout.println("");
        }

        JSONObject jsonObject = JSONObject.fromObject(map);
        stdout.println(jsonObject);

        return 0;
    }
}
