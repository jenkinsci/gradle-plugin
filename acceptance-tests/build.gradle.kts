import de.undercouch.gradle.tasks.download.Download
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.internal.os.OperatingSystem
import java.net.URL

plugins {
    java
    id("de.undercouch.download") version "5.6.0"
}

val ciJenkinsBuild: Boolean by (gradle as ExtensionAware).extra

java {
    // Only used for compilation. We don't rely on toolchain for running the tests,
    // as Jenkins ATH doesn't allow to specify the JAVA_HOME.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

group = "org.jenkins-ci.plugins"
description = "Acceptance tests of Gradle plugin"

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.jenkins-ci.org/public/")
    }
}

val gradlePlugin: Configuration by configurations.creating { isCanBeConsumed = false; isCanBeResolved = true }

dependencies {
    // same version as used by ATH
    annotationProcessor("org.jenkins-ci:annotation-indexer:1.18")

    implementation("org.jenkins-ci:acceptance-test-harness:6169.v8b_0662286b_b_7")

    testImplementation(platform("io.netty:netty-bom:4.2.2.Final"))
    testImplementation("io.ratpack:ratpack-test:2.0.0-rc-1")
    testCompileOnly("com.google.code.findbugs:jsr305:3.0.2")

    add(gradlePlugin.name, project(path = ":plugin", configuration = "gradlePluginJpi"))
}

val jenkinsVersions = listOf(
    JenkinsVersion.LATEST,
    JenkinsVersion.LATEST_LTS,
    JenkinsVersion.V2_440
)

jenkinsVersions
    .forEach { jenkinsVersion ->
        val downloadJenkinsTask =
            tasks.register<Download>("downloadJenkins${jenkinsVersion.label}") {
                src(jenkinsVersion.downloadUrl)
                dest(file("${project.gradle.gradleUserHomeDir}/jenkins-cache/${jenkinsVersion.version}/jenkins.war"))
                onlyIfModified(true)
                tempAndMove(true)
            }

        val testTask =
            if (jenkinsVersion.isDefault) {
                tasks.test
            } else {
                tasks.register<Test>("test${jenkinsVersion.label}")
            }

        testTask.configure {
            inputs.files(downloadJenkinsTask)
                .withPropertyName("jenkinsWar")
                .withNormalizer(ClasspathNormalizer::class)
            // Gradle doesn't support hpi extension, therefore reproducible-archives is required as well
            inputs.files(gradlePlugin)
                .withPropertyName("gradlePlugin")
                .withNormalizer(ClasspathNormalizer::class)

            onlyIf {
                // Do not run on Jenkins since acceptance tests don't work there for some reason, one of them:
                // https://github.com/jenkinsci/acceptance-test-harness/issues/1170
                //
                // Do not run on Windows as written here: https://github.com/jenkinsci/acceptance-test-harness/blob/master/docs/EXTERNAL.md
                !ciJenkinsBuild && !OperatingSystem.current().isWindows
            }

            systemProperties(
                mapOf(
                    "jdk.xml.xpathExprOpLimit" to 150
                )
            )

            doFirst {
                environment(
                    mapOf(
                        "JENKINS_WAR" to downloadJenkinsTask.get().outputs.files.singleFile,
                        "LOCAL_JARS" to gradlePlugin.singleFile,
                        "BROWSER" to "chrome"
                    )
                )
            }
        }
    }

data class JenkinsVersion(val version: String, val downloadUrl: URL) {

    companion object {

        private const val LATEST_VERSION = "2.504"
        private const val LATEST_LTS_VERSION = "2.492.3"
        private const val V2_440_VERSION = "2.440.3"

        private const val MIRROR = "https://updates.jenkins.io"

        private val JENKINS_VERSION_PATTERN = "^\\d+([.]\\d+)*?\$".toRegex()

        val LATEST = of("latest", LATEST_VERSION)
        val LATEST_LTS = of("latestLts", LATEST_LTS_VERSION)
        val V2_440 = of(V2_440_VERSION, V2_440_VERSION)

        private fun of(label: String, version: String): JenkinsVersion {
            if (!isJenkinsVersion(version)) {
                throw GradleException("Unsupported Jenkins version '${version}'")
            }
            val downloadUrl = "https://repo.jenkins-ci.org/public/org/jenkins-ci/main/jenkins-war/${version}/jenkins-war-${version}.war"
            return JenkinsVersion(label, URL(downloadUrl))
        }

        private fun isJenkinsVersion(version: String) = JENKINS_VERSION_PATTERN.matches(version)
    }

    val isDefault: Boolean
        get() = version == V2_440_VERSION

    val label: String
        get() = if (isJenkinsVersion(version)) {
            version.replace(".", "_")
        } else {
            version.split("-").joinToString(separator = "") { it.capitalized() }
        }

}
