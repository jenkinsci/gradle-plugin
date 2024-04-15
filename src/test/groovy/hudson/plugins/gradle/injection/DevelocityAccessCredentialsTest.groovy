package hudson.plugins.gradle.injection


import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Unroll
@Subject(DevelocityAccessCredentials)
class DevelocityAccessCredentialsTest extends Specification {

    def 'valid access key: #accessKey'(String accessKey) {
        expect:
        DevelocityAccessCredentials.isValid(accessKey)

        where:
        accessKey << [
            'server=secret',
            'server=secret ',
            'server = secret',
            ' server= secret',

            'sever1,server2,server3=secret',
            ' sever1, server2 , server3 = secret ',

            'server1=secret1;server2=secret2;server3=secret3',
            ' server1= secret1; server2 , sever3 = secret2 ;'
        ]
    }

    def 'invalid access key: #accessKey'(String accessKey) {
        expect:
        !DevelocityAccessCredentials.isValid(accessKey)

        where:
        accessKey << [
            null,
            '',
            ' ',
            'server=',
            '=secret',
            'secret',
            'server=secret; ',
            ';server=secret',
            'server1, server2,, server3 = secret '
        ]
    }

    def 'parse access key'() {
        when:
        def key = DevelocityAccessCredentials.parse(accessKey, 'host1')

        then:
        key == expected

        where:
        accessKey                          | expected
        'host1=key1'                       | Optional.of(DevelocityAccessCredentials.of('host1', 'key1'))
        'host1=key1;host2=key2'            | Optional.of(DevelocityAccessCredentials.of('host1', 'key1'))
        'host2=key2;host1=key1'            | Optional.of(DevelocityAccessCredentials.of('host1', 'key1'))
        'host2=key2;host1=key1;host3=key3' | Optional.of(DevelocityAccessCredentials.of('host1', 'key1'))
        ''                                 | Optional.empty()
        'host0=key0;host2=key2'            | Optional.empty()

    }
}
