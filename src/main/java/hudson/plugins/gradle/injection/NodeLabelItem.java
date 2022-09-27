package hudson.plugins.gradle.injection;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;
import java.io.Serializable;

public class NodeLabelItem extends AbstractDescribableImpl<NodeLabelItem> implements Serializable {

    private static final long serialVersionUID = -6196893952246142518L;

    private final String label;

    @DataBoundConstructor
    public NodeLabelItem(String label) {
        this.label = Util.fixEmptyAndTrim(label);
    }

    @CheckForNull
    public String getLabel() {
        return label;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<NodeLabelItem> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "";
        }
    }
}
