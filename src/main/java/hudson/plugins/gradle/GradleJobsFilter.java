package hudson.plugins.gradle;

import hudson.Extension;
import hudson.model.TopLevelItem;
import hudson.model.Descriptor;
import hudson.model.View;
import hudson.views.ViewJobFilter;

import java.util.ArrayList;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Custom view job filter used for filtering (that is keeping) all jobs using
 * the gradle builder.
 *
 * @author francois_ritaly
 */
public class GradleJobsFilter extends ViewJobFilter {

	@DataBoundConstructor
	public GradleJobsFilter() {
	}

	@Override
	public List<TopLevelItem> filter(List<TopLevelItem> added, List<TopLevelItem> all, View filteringView) {
		final List<TopLevelItem> selection = new ArrayList<TopLevelItem>();

		// Filter the 'added' list to only keep the jobs using the gradle builder
		for (final TopLevelItem item : added) {
			if (ViewJobFilterUtils.isGradleProject(item)) {
				selection.add(item);
			}
		}

		return selection;
	}

	@Extension
	public static class DescriptorImpl extends Descriptor<ViewJobFilter> {
		@Override
		public String getDisplayName() {
			return Messages.gradleJobsFilter_displayName();
		}

        @Override
        public String getHelpFile() {
            return "/plugin/gradle/help-ViewFilter-gradleJobs.html";
        }
	}
}