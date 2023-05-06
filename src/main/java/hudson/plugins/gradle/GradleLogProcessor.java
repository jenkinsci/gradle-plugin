package hudson.plugins.gradle;

import java.io.IOException;

public interface GradleLogProcessor {

    void processLogLine(String line) throws IOException;
}
