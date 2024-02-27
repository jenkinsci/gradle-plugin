package hudson.plugins.gradle.injection

import spock.lang.Specification
import spock.lang.Unroll

class MavenOptsDevelocityFilterTest extends Specification {

    private final static DV_SYS_PROPS = '-Dgradle.enterprise.allowUntrustedServer=false ' +
        '-Dgradle.scan.uploadInBackground=false -Dgradle.enterprise.url=https://scans.gradle.com'
    private final static DV_EXT_LIB = '/libs/gradle-enterprise-maven-extension.jar'
    private final static DV_CCUD_EXT_LIB = '/libs/common-custom-user-data-maven-extension.jar'

    @Unroll("#scenario")
    def "MAVEN_OPTS should be filtered according to applied extensions"() {
        given:
        def mavenOptsFilter = new MavenOptsDevelocityFilter(extensionsAlreadyApplied as Set, true)
        when:
        def filtered = mavenOptsFilter.filter(mavenOpts, false)

        then:
        filtered == expected

        where:
        scenario << [
            'DV extension globally injected and DV extension not already applied locally',
            'DV extension globally injected and DV extension already applied locally',
            'Random extension globally injected and DV extension already applied locally',
            'DV extension globally injected amongst others and DV extension already applied locally',
            'DV+CCUD extensions globally injected and DV+CCUD extensions not already applied locally',
            'DV+CCUD extensions globally injected and DV+CCUD extensions already applied locally',
            // A bit more contrieved cases
            'DV extension globally injected and CCUD extension already applied locally',
            'DV+CCUD extensions globally injected and CCUD extension already applied locally',
            'CCUD extensions globally injected and CCUD extension already applied locally',
        ]
        mavenOpts << [
            "-Dmaven.ext.class.path=${DV_EXT_LIB} ${DV_SYS_PROPS}",
            "-Dmaven.ext.class.path=${DV_EXT_LIB} ${DV_SYS_PROPS}",
            '-Dmaven.ext.class.path=/libs/some/other/ext.jar',
            "-Dmaven.ext.class.path=/libs/some/other/ext.jar:${DV_EXT_LIB}:/libs/some/other/ext2.jar ${DV_SYS_PROPS}",
            "-Dmaven.ext.class.path=${DV_EXT_LIB}:${DV_CCUD_EXT_LIB} ${DV_SYS_PROPS}",
            "-Dmaven.ext.class.path=${DV_EXT_LIB}:${DV_CCUD_EXT_LIB} ${DV_SYS_PROPS}",
            "-Dmaven.ext.class.path=${DV_EXT_LIB} ${DV_SYS_PROPS}",
            "-Dmaven.ext.class.path=${DV_EXT_LIB}:${DV_CCUD_EXT_LIB} ${DV_SYS_PROPS}",
            "-Dmaven.ext.class.path=${DV_CCUD_EXT_LIB}"
        ]
        extensionsAlreadyApplied << [
            [],
            [MavenExtension.GRADLE_ENTERPRISE],
            [MavenExtension.GRADLE_ENTERPRISE],
            [MavenExtension.GRADLE_ENTERPRISE],
            [],
            [MavenExtension.GRADLE_ENTERPRISE, MavenExtension.CCUD],
            [MavenExtension.CCUD],
            [MavenExtension.CCUD],
            [MavenExtension.CCUD]
        ]
        expected << [
            '-Dmaven.ext.class.path=/libs/gradle-enterprise-maven-extension.jar -Dgradle.enterprise.allowUntrustedServer=false -Dgradle.scan.uploadInBackground=false -Dgradle.enterprise.url=https://scans.gradle.com',
            '',
            '',
            '-Dmaven.ext.class.path=/libs/some/other/ext.jar:/libs/some/other/ext2.jar',
            '-Dmaven.ext.class.path=/libs/gradle-enterprise-maven-extension.jar:/libs/common-custom-user-data-maven-extension.jar -Dgradle.enterprise.allowUntrustedServer=false -Dgradle.scan.uploadInBackground=false -Dgradle.enterprise.url=https://scans.gradle.com',
            '',
            '-Dmaven.ext.class.path=/libs/gradle-enterprise-maven-extension.jar -Dgradle.enterprise.allowUntrustedServer=false -Dgradle.scan.uploadInBackground=false -Dgradle.enterprise.url=https://scans.gradle.com',
            '-Dmaven.ext.class.path=/libs/gradle-enterprise-maven-extension.jar -Dgradle.enterprise.allowUntrustedServer=false -Dgradle.scan.uploadInBackground=false -Dgradle.enterprise.url=https://scans.gradle.com',
            ''
        ]

    }

    def "MAVEN_OPTS should be filtered according to applied extensions when enforceUrl is true"() {
        given:
        def mavenOptsFilter = new MavenOptsDevelocityFilter(extensionsAlreadyApplied as Set, false)

        when:
        def filtered = mavenOptsFilter.filter(mavenOpts, true)

        then:
        filtered == expected

        where:
        mavenOpts                                                                 | extensionsAlreadyApplied                                | expected
        "-Dmaven.ext.class.path=${DV_EXT_LIB} ${DV_SYS_PROPS}"                    | [MavenExtension.GRADLE_ENTERPRISE]                      | '-Dgradle.enterprise.url=https://scans.gradle.com'
        "-Dmaven.ext.class.path=${DV_EXT_LIB}:${DV_CCUD_EXT_LIB} ${DV_SYS_PROPS}" | [MavenExtension.GRADLE_ENTERPRISE, MavenExtension.CCUD] | '-Dgradle.enterprise.url=https://scans.gradle.com'
    }

    def 'MAVEN_OPTS should be filtered on Windows'() {
        given:
        def mavenOptsFilter = new MavenOptsDevelocityFilter([MavenExtension.GRADLE_ENTERPRISE] as Set, false)

        when:
        def filtered = mavenOptsFilter.filter("-Dmaven.ext.class.path=/libs/some/other/ext.jar;${DV_EXT_LIB};/libs/some/other/ext2.jar ${DV_SYS_PROPS}", false)

        then:
        filtered == '-Dmaven.ext.class.path=/libs/some/other/ext.jar;/libs/some/other/ext2.jar'
    }

}
