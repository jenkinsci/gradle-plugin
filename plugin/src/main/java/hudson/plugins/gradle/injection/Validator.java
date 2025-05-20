package hudson.plugins.gradle.injection;

public interface Validator<T> {

    boolean isValid(T value);
}
