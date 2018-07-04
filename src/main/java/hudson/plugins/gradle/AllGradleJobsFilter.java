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
 * Custom view job filter used for adding all jobs using the gradle builder to a
 * view.
 *
 * @author francois_ritaly
 */
public class AllGradleJobsFilter extends ViewJobFilter {

	@DataBoundConstructor
	public AllGradleJobsFilter() {
	}

	@Override
	public List<TopLevelItem> filter(List<TopLevelItem> added, List<TopLevelItem> all, View filteringView) {
		final List<TopLevelItem> selection = new ArrayList<TopLevelItem>(added);

		// Complete the incoming added list with all jobs using the 'gradle'
		// builder
		for (final TopLevelItem item : all) {
			if (ViewJobFilterUtils.isGradleProject(item)) {
				if (!selection.contains(item)) {
					selection.add(item);
				}
			}
		}

		return selection;
	}

	@Extension
	public static class DescriptorImpl extends Descriptor<ViewJobFilter> {
		@Override
		public String getDisplayName() {
			return Messages.allGradleJobsFilter_displayName();
		}

		@Override
		public String getHelpFile() {
			return "/plugin/gradle/help-ViewFilter-allGradleJobs.html";
		}
	}
}