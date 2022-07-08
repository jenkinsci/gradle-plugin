package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.FilePath;

import java.io.IOException;
import java.io.InputStream;

public class EnvUtil {

    public static String getEnv(EnvVars env, String key) {
        return env != null ? env.get(key) : null;
    }

}
