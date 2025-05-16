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
        def creds = DevelocityAccessCredentials.parse(accessKey)

        then:
        creds == expected

        where:
        accessKey                          | expected
        'host1=key1'                       | DevelocityAccessCredentials.of([DevelocityAccessCredentials.HostnameAccessKey.of('host1', 'key1')])
        'host2=key2;host1=key1;host3=key3' | DevelocityAccessCredentials.of([
            DevelocityAccessCredentials.HostnameAccessKey.of('host2', 'key2'),
            DevelocityAccessCredentials.HostnameAccessKey.of('host1', 'key1'),
            DevelocityAccessCredentials.HostnameAccessKey.of('host3', 'key3')])
        ''                                 | DevelocityAccessCredentials.of([])
        'foo'                              | DevelocityAccessCredentials.of([])
    }

    def 'is empty'() {
        given:
        def creds = DevelocityAccessCredentials.of(keys)

        expect:
        creds.isEmpty() == expected

        where:
        keys                                                                | expected
        []                                                                  | true
        [DevelocityAccessCredentials.HostnameAccessKey.of('host1', 'key1')] | false
    }

    def 'is single'() {
        given:
        def creds = DevelocityAccessCredentials.of(keys)

        expect:
        creds.isSingleKey() == expected

        where:
        keys                                                                   | expected
        []                                                                     | false
        [DevelocityAccessCredentials.HostnameAccessKey.of('host1', 'key1')]    | true
        [
            DevelocityAccessCredentials.HostnameAccessKey.of('host1', 'key1'),
            DevelocityAccessCredentials.HostnameAccessKey.of('host2', 'key2'),
        ]                                                                      | false
    }

    def 'raw'() {
        given:
        def creds = DevelocityAccessCredentials.of([
            DevelocityAccessCredentials.HostnameAccessKey.of('host1', 'key1'),
            DevelocityAccessCredentials.HostnameAccessKey.of('host2', 'key2'),
        ])

        when:
        def raw = creds.getRaw()

        then:
        raw == 'host1=key1;host2=key2'
    }

    def 'find'() {
        given:
        def creds = DevelocityAccessCredentials.of([
            DevelocityAccessCredentials.HostnameAccessKey.of('host1', 'key1'),
            DevelocityAccessCredentials.HostnameAccessKey.of('host2', 'key2'),
        ])

        def found = creds.find(host)

        expect:
        found == expected

        where:
        host    | expected
        'host2' | Optional.of(DevelocityAccessCredentials.HostnameAccessKey.of('host2', 'key2'))
        'host3' | Optional.empty()
    }


}
