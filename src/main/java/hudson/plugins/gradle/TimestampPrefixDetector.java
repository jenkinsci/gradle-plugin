package hudson.plugins.gradle;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class TimestampPrefixDetector {

    static final String TimestampPattern = "\\[\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}Z\\] ";
    private static final Pattern TimestampPatternR = Pattern.compile("^(" + TimestampPattern + ").*\n?$");

    static String trimTimestampPrefix(int prefix, String line) {
        return line.substring(prefix);
    }

    static int detectTimestampPrefix(String line) {
        Matcher matcher = TimestampPatternR.matcher(line);
        if (matcher.matches()) {
            return matcher.group(1).length();
        }
        return 0;
    }


}
