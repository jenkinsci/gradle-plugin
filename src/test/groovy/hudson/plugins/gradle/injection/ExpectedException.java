package hudson.plugins.gradle.injection;

public class ExpectedException extends RuntimeException {

    public ExpectedException() {
        super("expected");
    }
}
