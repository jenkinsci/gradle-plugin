package hudson.plugins.gradle.injection;

import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Node;
import java.util.List;
import java.util.stream.Collectors;

final class MavenExtClasspathUtils {
    static final String SPACE = " ";

    private MavenExtClasspathUtils() {}

    static String constructExtClasspath(List<FilePath> extensions, boolean isUnix) {
        return extensions.stream().map(FilePath::getRemote).collect(Collectors.joining(getDelimiter(isUnix)));
    }

    static String getDelimiter(boolean isUnix) {
        return isUnix ? ":" : ";";
    }

    static boolean isUnix(Node node) {
        Computer computer = node.toComputer();
        return isUnix(computer);
    }

    static boolean isUnix(Computer computer) {
        return computer == null || Boolean.TRUE.equals(computer.isUnix());
    }
}
