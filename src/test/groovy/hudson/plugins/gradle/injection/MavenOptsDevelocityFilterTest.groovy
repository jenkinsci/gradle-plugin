package hudson.plugins.gradle.injection

import spock.lang.Specification
import spock.lang.Unroll

import static hudson.plugins.gradle.injection.InitScriptVariables.DEVELOCITY_URL
import static hudson.plugins.gradle.injection.MavenInjectionAware.BUILD_SCAN_UPLOAD_IN_BACKGROUND_PROPERTY_KEY
import static hudson.plugins.gradle.injection.MavenInjectionAware.DEVELOCITY_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY
import static hudson.plugins.gradle.injection.MavenInjectionAware.DEVELOCITY_UPLOAD_IN_BACKGROUND_PROPERTY_KEY
import static hudson.plugins.gradle.injection.MavenInjectionAware.DEVELOCITY_URL_PROPERTY_KEY
import static hudson.plugins.gradle.injection.MavenInjectionAware.GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY
import static hudson.plugins.gradle.injection.MavenInjectionAware.GRADLE_ENTERPRISE_URL_PROPERTY_KEY

class MavenOptsDevelocityFilterTest extends Specification {

    private final static DV_SYS_PROPS = [
        new SystemProperty(DEVELOCITY_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY, 'false'),
        new SystemProperty(DEVELOCITY_UPLOAD_IN_BACKGROUND_PROPERTY_KEY, 'false'),
        new SystemProperty(DEVELOCITY_URL_PROPERTY_KEY, 'https://scans.gradle.com'),
        new SystemProperty(GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY, 'false'),
        new SystemProperty(GRADLE_ENTERPRISE_URL_PROPERTY_KEY, 'https://scans.gradle.com'),
        new SystemProperty(BUILD_SCAN_UPLOAD_IN_BACKGROUND_PROPERTY_KEY, 'false')
    ].collect { it.asString() }.join(' ')

    private final static DV_EXT_LIB = '/libs/develocity-maven-extension.jar'
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
            [MavenExtension.DEVELOCITY],
            [MavenExtension.DEVELOCITY],
            [MavenExtension.DEVELOCITY],
            [],
            [MavenExtension.DEVELOCITY, MavenExtension.CCUD],
            [MavenExtension.CCUD],
            [MavenExtension.CCUD],
            [MavenExtension.CCUD]
        ]
        expected << [
            "-Dmaven.ext.class.path=/libs/develocity-maven-extension.jar $DV_SYS_PROPS",
            '',
            '',
            '-Dmaven.ext.class.path=/libs/some/other/ext.jar:/libs/some/other/ext2.jar',
            "-Dmaven.ext.class.path=/libs/develocity-maven-extension.jar:/libs/common-custom-user-data-maven-extension.jar $DV_SYS_PROPS",
            '',
            "-Dmaven.ext.class.path=/libs/develocity-maven-extension.jar $DV_SYS_PROPS",
            "-Dmaven.ext.class.path=/libs/develocity-maven-extension.jar $DV_SYS_PROPS",
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
        "-Dmaven.ext.class.path=${DV_EXT_LIB} ${DV_SYS_PROPS}"                    | [MavenExtension.DEVELOCITY]                             | DEVELOCITY_URL.sysProp('https://scans.gradle.com')
        "-Dmaven.ext.class.path=${DV_EXT_LIB}:${DV_CCUD_EXT_LIB} ${DV_SYS_PROPS}" | [MavenExtension.DEVELOCITY, MavenExtension.CCUD]        | DEVELOCITY_URL.sysProp('https://scans.gradle.com')
    }

    def 'MAVEN_OPTS should be filtered on Windows'() {
        given:
        def mavenOptsFilter = new MavenOptsDevelocityFilter([MavenExtension.DEVELOCITY] as Set, false)

        when:
        def filtered = mavenOptsFilter.filter("-Dmaven.ext.class.path=/libs/some/other/ext.jar;${DV_EXT_LIB};/libs/some/other/ext2.jar ${DV_SYS_PROPS}", false)

        then:
        filtered == '-Dmaven.ext.class.path=/libs/some/other/ext.jar;/libs/some/other/ext2.jar'
    }

}
