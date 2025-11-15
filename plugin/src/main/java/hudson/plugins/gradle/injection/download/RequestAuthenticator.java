package hudson.plugins.gradle.injection.download;

import okhttp3.Credentials;
import okhttp3.Request;

public interface RequestAuthenticator {

    RequestAuthenticator NONE = request -> {
    };

    void authenticate(Request.Builder request);

    static RequestAuthenticator basic(String username, String password) {
        return request -> request.addHeader("Authorization", Credentials.basic(username, password));
    }
}
