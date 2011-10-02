package hudson.plugins.gradle;

import hudson.Extension;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;

import java.util.Collection;
import java.util.HashSet;
import java.util.regex.Pattern;

public final class GradleTaskNote extends ConsoleNote {

    private static Collection<String> progressStatuses = new HashSet<String>();

    static {
        // add to this collection if other words should be contained.
        progressStatuses.add("UP-TO-DATE");
        progressStatuses.add("SKIPPED");
    }

    @Override
    public ConsoleAnnotator annotate(Object context, MarkupText text,
                                     int charPos) {
        // still under development. too early to put into production
        if (!ENABLED)
            return null;

        MarkupText.SubText t = text.findToken(Pattern
                .compile("^:([^:]\\S*)(\\s*)(\\S*)"));
        if (t == null) {
            return null;
        }

        String task = t.group(1);
        String delimiterSpace = t.group(2);
        String progressStatus = t.group(3);

        // annotate task and progress status
        if (task != null && !task.isEmpty()) {
            t.addMarkup(1, task.length() + 1, "<b class=gradle-task>", "</b>");
        }
        if (progressStatus != null && !progressStatus.isEmpty()
                && progressStatuses.contains(progressStatus)) {
            t.addMarkup(task.length() + delimiterSpace.length() + 1,
                    text.length(), "<span class=gradle-task-progress-status>",
                    "</span>");
        }

        return null;
    }

    @Extension
    public static final class DescriptorImpl extends
            ConsoleAnnotationDescriptor {
        public String getDisplayName() {
            return "Gradle tasks";
        }
    }

    public static boolean ENABLED = !Boolean.getBoolean(GradleTaskNote.class
            .getName() + ".disabled");
}
