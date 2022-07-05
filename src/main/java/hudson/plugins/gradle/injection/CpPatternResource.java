package hudson.plugins.gradle.injection;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

public class CpPatternResource {

    private final Pattern filenamePattern;
    private String resolved;

    public CpPatternResource(Pattern filenamePattern) {
        this.filenamePattern = filenamePattern;
    }

    public String resolve() {
        if (resolved == null) {
            resolved = findResource();
        }
        return resolved;
    }

    private String findResource() {
        String notFound = "Could not find resource with filename pattern: " + filenamePattern;
        try {
            String directoryName = "hudson/plugins/gradle/injection/";
            Enumeration<URL> resources = CopyUtil.class.getClassLoader().getResources(directoryName);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                if (url.getProtocol().equals("file")) {
                    Optional<String> found = findInDir(url);
                    if (found.isPresent()) {
                        return found.get();
                    }
                } else if (url.getProtocol().equals("jar")) {
                    Optional<String> found = findInJar(directoryName, url);
                    if (found.isPresent()) {
                        return found.get();
                    }
                }
            }
        } catch (URISyntaxException | IOException e) {
            throw new IllegalStateException(notFound, e);
        }
        throw new IllegalStateException(notFound);
    }

    private Optional<String> findInJar(String directoryName, URL url) throws IOException {
        try (JarFile jar = new JarFile(URLDecoder.decode(toJarPath(url), StandardCharsets.UTF_8.name()))) {
            return jar.stream()
                .map(e -> e.getName().replace(directoryName, ""))
                .filter(f -> filenamePattern.matcher(f).matches()).findFirst();
        }
    }

    static String toJarPath(URL url) {
        String path = url.getPath();
        return path.substring("file:".length(), path.indexOf("!"));
    }

    private Optional<String> findInDir(URL url) throws URISyntaxException {
        File file = Paths.get(url.toURI()).toFile();
        return Optional.ofNullable(file.listFiles()).flatMap(files ->
            Arrays.stream(files).map(File::getName).filter(f -> filenamePattern.matcher(f).matches()).findFirst());
    }
}
