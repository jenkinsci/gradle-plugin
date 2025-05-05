package hudson.plugins.gradle.injection;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import java.io.Serializable;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.kohsuke.stapler.DataBoundConstructor;

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

        @Nonnull
        @Override
        public String getDisplayName() {
            return "";
        }
    }
}
