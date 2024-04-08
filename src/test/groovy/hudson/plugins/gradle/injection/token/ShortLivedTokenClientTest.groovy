package hudson.plugins.gradle.injection.token

import hudson.plugins.gradle.injection.DevelocityAccessKey
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

        when:
        def token = new ShortLivedTokenClient().get(mockDevelocity.address.toString(), DevelocityAccessKey.parse('localhost=xyz'), null)

        then:
        token.get().key == 'some-token'
        token.get().hostname == mockDevelocity.address.host
    }

    def "get token with expiry"() {
        given:
        def mockDevelocity = GroovyEmbeddedApp.of {
            handlers {
                post("api/auth/token") {
                    if (request.queryParams.get('expiry') == '3') {
                        response.status(200)
                        response.send('some-token')
                    }
                }
            }
        }

        when:
        def token = new ShortLivedTokenClient().get(mockDevelocity.address.toString(), DevelocityAccessKey.parse('localhost=xyz'), 3)

        then:
        token.get().key == 'some-token'
        token.get().hostname == mockDevelocity.address.host
    }

    def "get token fails"() {
        given:
        def mockDevelocity = GroovyEmbeddedApp.of {
            handlers {
                post("api/auth/token") {
                    response.status(401)
                    response.send('{"status":401,"type":"urn:gradle:develocity:api:problems:client-error","title":"Something was wrong with the request."}')
                }
            }
        }

        when:
        def token = new ShortLivedTokenClient().get(mockDevelocity.address.toString(), DevelocityAccessKey.parse('localhost=xyz'), null)

        then:
        !token.isPresent()
    }

    def "get token sever fails with exception"() {
        when:
        def token = new ShortLivedTokenClient().get('http://localhost:8888', DevelocityAccessKey.parse('localhost=xyz'), null)

        then:
        !token.isPresent()
    }

}
