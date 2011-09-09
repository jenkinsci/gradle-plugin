package hudson.plugins.gradle;

import hudson.Extension;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;

import java.util.regex.Pattern;

public final class GradleTargetNote extends ConsoleNote {
	public GradleTargetNote() {
	}

	@Override
	public ConsoleAnnotator annotate(Object context, MarkupText text,
			int charPos) {
		// still under development. too early to put into production
		if (!ENABLED)
			return null;

		// TODO confirm regex
		// MarkupText.SubText t = text.findToken(Pattern.compile(".*(?=:)"));
		MarkupText.SubText t = text.findToken(Pattern.compile(":(.+)"));
		if (t != null)
			t.addMarkup(0, t.length(), "<b class=gradle-target>", "</b>");
		return null;
	}

	@Extension
	public static final class DescriptorImpl extends
			ConsoleAnnotationDescriptor {
		public String getDisplayName() {
			return "Gradle targets";
		}
	}

	public static boolean ENABLED = !Boolean.getBoolean(GradleTargetNote.class
			.getName() + ".disabled");
}
