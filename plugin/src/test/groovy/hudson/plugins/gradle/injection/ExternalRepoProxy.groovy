package hudson.plugins.gradle.injection


import org.apache.commons.lang3.StringUtils
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.test.embed.EmbeddedApp

class ExternalRepoProxy implements AutoCloseable {

    private final EmbeddedApp app

    ExternalRepoProxy(URI remote) {
        this.app = GroovyEmbeddedApp.of {
            serverConfig {
                port(0)
            }
            handlers {
                all {
                    def remoteBase = StringUtils.removeEnd(remote.toString(), "/")
                    def requestUri = request.uri
                    redirect(URI.create(remoteBase + requestUri))
                }
            }
        }
    }

    URI getAddress() {
        app.getAddress()
    }

    @Override
    void close() throws Exception {
        app.close()
    }
}
