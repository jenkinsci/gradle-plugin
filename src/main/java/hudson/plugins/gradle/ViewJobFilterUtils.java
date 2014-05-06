package hudson.plugins.gradle;

import hudson.matrix.MatrixProject;
import hudson.model.TopLevelItem;
import hudson.model.Project;

import org.apache.commons.lang.Validate;

final class ViewJobFilterUtils {

	private ViewJobFilterUtils() {
	}

	/**
	 * Tells whether the given top level item uses the gradle builder.
	 *
	 * @param item
	 *            a top level item to test. Can't be null.
	 * @return whether the given top level item uses the gradle builder.
	 */
	static boolean isGradleProject(TopLevelItem item) {
		Validate.notNull(item, "The given top level item is null");

		if (item instanceof Project<?,?>) {
			// This matches FreeStyleProject & MatrixConfiguration too
			final Project<?,?> project = (Project<?,?>) item;

			return (project.getBuildersList().get(Gradle.class) != null);
		} else if (item instanceof MatrixProject) {
			final MatrixProject project = (MatrixProject) item;

			return (project.getBuildersList().get(Gradle.class) != null);
		}

		return false;
	}
}
