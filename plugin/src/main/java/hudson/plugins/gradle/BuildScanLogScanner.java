package hudson.plugins.gradle;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuildScanLogScanner {

    private static final Logger LOGGER = Logger.getLogger(BuildScanLogScanner.class.getName());

    private static final Pattern BUILD_SCAN_PATTERN = Pattern.compile("Publishing (Build Scan|build scan|build information)(?: to Develocity)?\\.\\.\\.");
    private static final Pattern URL_PATTERN = Pattern.compile(".*(?:\\[INFO] )?(https?://.*/s/.*)");
    private static final int LINES_TO_SCAN = 1000;

    private final BuildScanPublishedListener listener;

    private int linesSinceBuildScanPublishingMessage = Integer.MAX_VALUE;

    public BuildScanLogScanner(BuildScanPublishedListener listener) {
        this.listener = listener;
    }

    void scanLine(String line) {
        LOGGER.log(Level.FINE, "Scanning line: {0}", line);

        if (linesSinceBuildScanPublishingMessage < LINES_TO_SCAN) {
            linesSinceBuildScanPublishingMessage++;
            LOGGER.log(Level.FINE, "Within scan window, lines since publishing message: {0}", linesSinceBuildScanPublishingMessage);
            tryFindBuildScanUrl(line, url -> {
                LOGGER.log(Level.FINE, "Found build scan URL: {0}", url);
                linesSinceBuildScanPublishingMessage = Integer.MAX_VALUE;
                listener.onBuildScanPublished(url);
            });
        }
        if (BUILD_SCAN_PATTERN.matcher(line).find()) {
            LOGGER.log(Level.FINE, "Detected build scan publishing message: {0}", line);
            linesSinceBuildScanPublishingMessage = 0;
        }

    }

    private static void tryFindBuildScanUrl(String text, Consumer<String> action) {
        Matcher matcher = URL_PATTERN.matcher(text);
        if (matcher.matches()) {
            action.accept(matcher.group(1));
        }
    }
}
