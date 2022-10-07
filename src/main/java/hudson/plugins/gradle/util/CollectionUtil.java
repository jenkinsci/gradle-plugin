package hudson.plugins.gradle.util;

import java.util.Collection;
import java.util.stream.Stream;

public final class CollectionUtil {

    private CollectionUtil() {
    }

    public static <T> Stream<T> safeStream(Collection<T> col) {
        if (col == null) {
            return Stream.empty();
        }
        return col.stream();
    }
}
