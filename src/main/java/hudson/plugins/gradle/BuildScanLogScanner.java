package hudson.plugins.gradle;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuildScanLogScanner {
    private final BuildScanPublishedListener listener;
    private int linesSinceBuildScanPublishingMessage = Integer.MAX_VALUE;
    private static final Pattern BUILD_SCAN_PATTERN = Pattern.compile("Publishing (build scan|build information)\\.\\.\\.");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S*");

    public BuildScanLogScanner(BuildScanPublishedListener listener) {
        this.listener = listener;
    }

    void scanLine(String line) {
        if (linesSinceBuildScanPublishingMessage < 10) {
            linesSinceBuildScanPublishingMessage++;
            Matcher matcher = URL_PATTERN.matcher(line);
            if (matcher.find()) {
                linesSinceBuildScanPublishingMessage = Integer.MAX_VALUE;
                String buildScanUrl = matcher.group();
                listener.onBuildScanPublished(buildScanUrl);
            }
        }
        if (BUILD_SCAN_PATTERN.matcher(line).find()) {
            linesSinceBuildScanPublishingMessage = 0;
        }

    }
}
