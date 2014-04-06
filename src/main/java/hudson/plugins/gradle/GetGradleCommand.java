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
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* Add a new command to the jenkins CLI, get-gradle.
 *
 * This lists a subset of the known information about either all gradle
 * installations, or just the requested gradle installation, formatted as
 * JSON.  On the jenkins server, this information is stored in the file
 * hudson.plugins.gradle.Gradle.xml.
 *
 * In the case of all installations, return a map, where the keys are the
 * names of all of the gradle installations, and the values are an array of
 * id's for all of the installers that correspond to the given installation.
 *
 * In the case of a single installation, return just the array of id's for for
 * all of the installers that correspond to the selected installation.
 */
@Extension
public class GetGradleCommand extends CLICommand {

    /* The name of the CLI command
     */
    @Override
    public String getName() {
        return "get-gradle";
    }

    /* A short description of the CLI command
     */
    @Override
    public String getShortDescription() {
        return "List available gradle installations";
    }

    /* The name argument to the CLI command
     *
     * Optional.  The name of the gradle installation. If name is not
     * provided, list all available installations.
     */
    @Option(name = "--name",
            required = false,
            usage = "[Optional] The name of the gradle installation.  If name is not provided, list all available installations.")
    public String name = null;

    /* Print usage statement
     */
    @Option(name = "--help",
            required = false,
            usage = "Print this usage statement")
    public boolean help = false;

    // return values
    private static final int OK = 0;
    private static final int NOT_FOUND = 1;

    /* Execute the CLI command.
     *
     * On success, results are returned to stdout.
     * On error, an error message is written to stderr.
     */
    @Override
    protected int run() throws Exception {
        if (help) {
            printUsage(stderr, new CmdLineParser(this));
            return OK;
        }

        GradleInstallation[] installations =
            Hudson.getInstance().getDescriptorByType(GradleInstallation.DescriptorImpl.class).getInstallations();

        Map<String,List<String>> map = new HashMap<String,List<String>>();

        for (GradleInstallation installation: installations) {
            for (Map.Entry<ToolPropertyDescriptor,ToolProperty<?>> entry:
                     installation.getProperties().toMap().entrySet()) {
                DescribableList<ToolInstaller,Descriptor<ToolInstaller>> installers =
                    ((InstallSourceProperty)entry.getValue()).installers;
                List<String> list = new ArrayList<String>();
                for (ToolInstaller installer: installers) {
                    list.add(((DownloadFromUrlInstaller)installer).id);
                }
                map.put(installation.getName(), list);
            }
        }

        if (name == null) {
            // return all gradle installations
            JSONObject jsonObject = JSONObject.fromObject(map);
            stdout.println(jsonObject);
        } else {
            List<String> returnList = map.get(name);
            if (returnList == null) {
                // requested installation not found
                stderr.println("Requested gradle installation not found: " + name);
                return NOT_FOUND;
            } else {
                // return the one found gradle installation
                JSONArray jsonArray = JSONArray.fromObject(returnList);
                stdout.println(jsonArray);
            }
        }

        // if we get this far, we have succeeded
        return OK;
    }
}
