package hudson.plugins.gradle;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuildScanLogScanner {
    private static final Pattern BUILD_SCAN_PATTERN = Pattern.compile("Publishing (build scan|build information)\\.\\.\\.");
    private static final Pattern URL_PATTERN = Pattern.compile(".*(?:\\[INFO] )?(https?://.*/s/.*)");
    private static final int LINES_TO_SCAN = 1000;
    private final BuildScanPublishedListener listener;
    private int linesSinceBuildScanPublishingMessage = Integer.MAX_VALUE;

    public BuildScanLogScanner(BuildScanPublishedListener listener) {
        this.listener = listener;
    }

    void scanLine(String line) {
        if (linesSinceBuildScanPublishingMessage < LINES_TO_SCAN) {
            linesSinceBuildScanPublishingMessage++;
            tryFindBuildScanUrl(line).ifPresent(url -> {
                linesSinceBuildScanPublishingMessage = Integer.MAX_VALUE;
                listener.onBuildScanPublished(url);
            });
        }
        if (BUILD_SCAN_PATTERN.matcher(line).find()) {
            linesSinceBuildScanPublishingMessage = 0;
        }

    }

    private static Optional<String> tryFindBuildScanUrl(String text) {
        Matcher matcher = URL_PATTERN.matcher(text);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
