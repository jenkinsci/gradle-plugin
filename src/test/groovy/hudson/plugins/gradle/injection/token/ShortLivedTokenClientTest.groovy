package hudson.plugins.gradle.injection.token

import hudson.plugins.gradle.injection.DevelocityAccessCredentials
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslProvider
import io.netty.handler.ssl.util.SelfSignedCertificate
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.server.ServerConfig
import spock.lang.Specification

class ShortLivedTokenClientTest extends Specification {

    def "get token"() {
        given:
        def mockDevelocity = GroovyEmbeddedApp.of {
            handlers {
                post("api/auth/token") {
                    response.status(200)
                    response.send('some-token')
                }
            }
        }
        def key = DevelocityAccessCredentials.HostnameAccessKey.of('localhost', 'xyz')

        when:
        def token = new ShortLivedTokenClient(false).get(mockDevelocity.address.toString(), key, null)

        then:
        token.get().key == 'some-token'
        token.get().hostname == mockDevelocity.address.host
    }

    def "get token with expiry"() {
        given:
        def mockDevelocity = GroovyEmbeddedApp.of {
            handlers {
                post("api/auth/token") {
                    if (request.queryParams.get('expiresInHours') == '3') {
                        response.status(200)
                        response.send('some-token')
                    }
                }
            }
        }
        def key = DevelocityAccessCredentials.HostnameAccessKey.of('localhost', 'xyz')

        when:
        def token = new ShortLivedTokenClient(false).get(mockDevelocity.address.toString(), key, 3)

        then:
        token.get().key == 'some-token'
        token.get().hostname == mockDevelocity.address.host
    }

    def "get token fails with 401"() {
        given:
        def mockDevelocity = GroovyEmbeddedApp.of {
            handlers {
                post("api/auth/token") {
                    response.status(401)
                    response.send('{"status":401,"type":"urn:gradle:develocity:api:problems:client-error","title":"Something was wrong with the request."}')
                }
            }
        }
        def key = DevelocityAccessCredentials.HostnameAccessKey.of('localhost', 'xyz')

        when:
        def token = new ShortLivedTokenClient(false).get(mockDevelocity.address.toString(), key, null)

        then:
        !token.isPresent()
    }

    def "get token fails after retries"() {
        given:
        def requestCounter = 0
        def mockDevelocity = GroovyEmbeddedApp.of {
            handlers {
                post("api/auth/token") {
                    requestCounter++
                    response.status(500)
                    response.send('Internal error')
                }
            }
        }
        def key = DevelocityAccessCredentials.HostnameAccessKey.of('localhost', 'xyz')

        when:
        def token = new ShortLivedTokenClient(false).get(mockDevelocity.address.toString(), key, null)

        then:
        requestCounter == 3
        !token.isPresent()
    }

    def "get token sever fails with exception"() {
        given:
        def key = DevelocityAccessCredentials.HostnameAccessKey.of('localhost', 'xyz')

        when:
        def token = new ShortLivedTokenClient(false).get('http://localhost:8888', key, null)

        then:
        !token.isPresent()
    }

    def "get token successfully after retry"() {
        given:
        def firstRequest = true
        def mockDevelocity = GroovyEmbeddedApp.of {
            handlers {
                post("api/auth/token") {
                    if (firstRequest) {
                        response.status(503)
                        response.send('Not available')
                        firstRequest = false
                    } else {
                        response.status(200)
                        response.send('some-token')
                    }
                }
            }
        }
        def key = DevelocityAccessCredentials.HostnameAccessKey.of('localhost', 'xyz')

        when:
        def token = new ShortLivedTokenClient(false).get(mockDevelocity.address.toString(), key, null)

        then:
        token.get().key == 'some-token'
        token.get().hostname == mockDevelocity.address.host
    }

    def "get token for an untrusted server - #allowUntrustedServer"(boolean allowUntrustedServer) {
        given:
        def mockDevelocity = GroovyEmbeddedApp.of {
            serverConfig(ServerConfig.builder().tap {
                def cert = new SelfSignedCertificate('localhost')
                def sslContext = SslContextBuilder.forServer(cert.certificate(), cert.privateKey())
                        .sslProvider(SslProvider.JDK)
                        .build() as SslContext
                ssl(sslContext)
            })
            handlers {
                post("api/auth/token") {
                    response.status(200)
                    response.send('some-token')
                }
            }
        }
        def key = DevelocityAccessCredentials.HostnameAccessKey.of('localhost', 'xyz')

        when:
        def token = new ShortLivedTokenClient(allowUntrustedServer).get(mockDevelocity.address.toString(), key, null)

        then:
        if (allowUntrustedServer) {
            assert token.get().key == 'some-token'
            assert token.get().hostname == mockDevelocity.address.host
        } else {
            assert !token.isPresent()
        }

        mockDevelocity.server.stop()

        where:
        allowUntrustedServer << [true, false]
    }

}
