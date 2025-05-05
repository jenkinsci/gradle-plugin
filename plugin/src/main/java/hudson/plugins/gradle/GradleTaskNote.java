package hudson.plugins.gradle;

import static hudson.plugins.gradle.TimestampPrefixDetector.TimestampPattern;

import hudson.Extension;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.regex.Pattern;

public final class GradleTaskNote extends ConsoleNote {

    private static final Collection<String> progressStatuses = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("UP-TO-DATE", "SKIPPED", "FROM-CACHE", "NO-SOURCE")));

    private static final Pattern TASK_PATTERN_1 =
            Pattern.compile("^(?:" + TimestampPattern + ")?:([^:]\\S*)(\\s*)(\\S*)");
    private static final Pattern TASK_PATTERN_2 =
            Pattern.compile("^(?:" + TimestampPattern + ")?> Task :([^:]\\S*)(\\s*)(\\S*)");

    @Override
    public ConsoleAnnotator annotate(Object context, MarkupText text, int charPos) {
        // still under development. too early to put into production
        if (!ENABLED) return null;

        int timestampPrefix = TimestampPrefixDetector.detectTimestampPrefix(text.getText());
        int prefixLength = 1;
        MarkupText.SubText t = text.findToken(TASK_PATTERN_1);
        if (t == null) {
            t = text.findToken(TASK_PATTERN_2);
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
            t.addMarkup(
                    timestampPrefix + 1,
                    timestampPrefix + task.length() + prefixLength,
                    "<b class=\"gradle-task\">",
                    "</b>");
            if (progressStatus != null && !progressStatus.isEmpty() && progressStatuses.contains(progressStatus)) {
                t.addMarkup(
                        timestampPrefix + task.length() + delimiterSpace.length() + prefixLength,
                        text.length(),
                        "<span class=\"gradle-task-progress-status\">",
                        "</span>");
            }
        }

        return null;
    }

    @Extension
    public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {
        public String getDisplayName() {
            return "Gradle tasks";
        }
    }

    /** Non-private for use in tests. */
    static boolean ENABLED = !Boolean.getBoolean(GradleTaskNote.class.getName() + ".disabled");
}
