package hudson.plugins.gradle.injection


import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Unroll
@Subject(DevelocityAccessKey)
class DevelocityAccessKeyTest extends Specification {

    def 'valid access key: #accessKey'(String accessKey) {
        expect:
        DevelocityAccessKey.isValid(accessKey)

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
        !DevelocityAccessKey.isValid(accessKey)

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
}
