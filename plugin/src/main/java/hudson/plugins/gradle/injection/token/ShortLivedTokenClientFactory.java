package hudson.plugins.gradle.injection.token;

/**
 * Factory is needed to create a new instance based on `allowUntrusted` which can be changed at runtime.
 */
public class ShortLivedTokenClientFactory {

    public ShortLivedTokenClient create(boolean allowUntrusted) {
        return new ShortLivedTokenClient(allowUntrusted);
    }

}
