package hudson.plugins.gradle;

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;

import java.util.Collection;
import java.util.regex.Pattern;

public final class GradleTaskNote extends ConsoleNote {

    private static final Collection<String> progressStatuses = ImmutableSet.of(
            "UP-TO-DATE",
            "SKIPPED",
            "FROM-CACHE",
            "NO-SOURCE"
    );

    @Override
    public ConsoleAnnotator annotate(Object context, MarkupText text,
                                     int charPos) {
        // still under development. too early to put into production
        if (!ENABLED)
            return null;

        int prefixLength = 1;
        MarkupText.SubText t = text.findToken(Pattern
                .compile("^:([^:]\\S*)(\\s*)(\\S*)"));
        if (t == null) {
            t = text.findToken(Pattern
                .compile("^> Task :([^:]\\S*)(\\s*)(\\S*)"));
            prefixLength = 8;
        }
        if (t == null) {
            return null;
        }

        String task = t.group(1);
        String delimiterSpace = t.group(2);
        String progressStatus = t.group(3);

        // annotate task and progress status
        if (task != null && !task.isEmpty()) {
            t.addMarkup(1, task.length() + prefixLength, "<b class=gradle-task>", "</b>");
        }
        if (progressStatus != null && !progressStatus.isEmpty()
                && progressStatuses.contains(progressStatus)) {
            t.addMarkup(task.length() + delimiterSpace.length() + prefixLength,
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

    /** Non-private for use in tests. */
    static boolean ENABLED = !Boolean.getBoolean(GradleTaskNote.class
            .getName() + ".disabled");
}
