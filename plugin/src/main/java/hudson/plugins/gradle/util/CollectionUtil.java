package hudson.plugins.gradle.util;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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

    @CheckForNull
    public static <T> List<T> unmodifiableCopy(@Nullable List<T> list) {
        if (list == null) {
            return null;
        }
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(list));
    }
}
