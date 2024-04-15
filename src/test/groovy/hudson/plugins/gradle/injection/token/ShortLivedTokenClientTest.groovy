package hudson.plugins.gradle.injection.token

import hudson.plugins.gradle.injection.DevelocityAccessCredentials
import ratpack.groovy.test.embed.GroovyEmbeddedApp
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
        def key = DevelocityAccessCredentials.parse('localhost=xyz', 'localhost').get()

        when:
        def token = new ShortLivedTokenClient().get(mockDevelocity.address.toString(), key, null)

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
        def key = DevelocityAccessCredentials.parse('localhost=xyz', 'localhost').get()

        when:
        def token = new ShortLivedTokenClient().get(mockDevelocity.address.toString(), key, 3)

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
        def key = DevelocityAccessCredentials.parse('localhost=xyz', 'localhost').get()

        when:
        def token = new ShortLivedTokenClient().get(mockDevelocity.address.toString(), key, null)

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
        def key = DevelocityAccessCredentials.parse('localhost=xyz', 'localhost').get()

        when:
        def token = new ShortLivedTokenClient().get(mockDevelocity.address.toString(), key, null)

        then:
        requestCounter == 3
        !token.isPresent()
    }

    def "get token sever fails with exception"() {
        given:
        def key = DevelocityAccessCredentials.parse('localhost=xyz', 'localhost').get()

        when:
        def token = new ShortLivedTokenClient().get('http://localhost:8888', key, null)

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
        def key = DevelocityAccessCredentials.parse('localhost=xyz', 'localhost').get()

        when:
        def token = new ShortLivedTokenClient().get(mockDevelocity.address.toString(), key, null)

        then:
        token.get().key == 'some-token'
        token.get().hostname == mockDevelocity.address.host
    }

}
