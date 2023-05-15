import de.undercouch.gradle.tasks.download.Download
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.internal.os.OperatingSystem
import java.net.URL

plugins {
    java
    id("buildlogic.chromedriver")
    id("de.undercouch.download") version "5.4.0"
}

val ciJenkinsBuild: Boolean by (gradle as ExtensionAware).extra

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
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
    annotationProcessor("org.jenkins-ci:annotation-indexer:1.12")

    implementation("org.jenkins-ci:acceptance-test-harness:5581.vfd8e43f46a_03")

    testImplementation(platform("io.netty:netty-bom:4.1.92.Final"))
    testImplementation("io.ratpack:ratpack-test:2.0.0-rc-1")

    add(gradlePlugin.name, project(path = ":", configuration = "gradlePluginJpi"))
}

val currentJava = JavaVersion.current()

val jenkinsVersions = listOf(
    JenkinsVersion.LATEST,
    JenkinsVersion.LATEST_LTS,
    JenkinsVersion.V2_375
)

jenkinsVersions
    .filter { jenkinsVersion ->
        jenkinsVersion.isDefault || currentJava.isCompatibleWith(jenkinsVersion.requiredJavaVersion)
    }
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
                // Do not run on Windows as written here: https://github.com/jenkinsci/acceptance-test-harness/blob/master/docs/EXTERNAL.md
                !OperatingSystem.current().isWindows
            }

            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(jenkinsVersion.javaVersion)
            })

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
                        "BROWSER" to if (ciJenkinsBuild) "firefox-container" else "chrome"
                    )
                )
            }
        }
    }

data class JenkinsVersion(val version: String, val downloadUrl: URL, val javaVersion: JavaLanguageVersion) {

    companion object {

        private const val LATEST_VERSION = "latest"
        private const val LATEST_LTS_VERSION = "latest-lts"
        private const val V2_375_VERSION = "2.375.4"

        private const val MIRROR = "https://get.jenkins.io"

        private val JENKINS_VERSION_PATTERN = "^\\d+([.]\\d+)*?\$".toRegex()

        private val JAVA_11 = JavaLanguageVersion.of(11)

        val LATEST = of(LATEST_VERSION)
        val LATEST_LTS = of(LATEST_LTS_VERSION)
        val V2_375 = of(V2_375_VERSION)

        private fun of(version: String, javaVersion: JavaLanguageVersion = JAVA_11): JenkinsVersion {
            val downloadUrl =
                when (version) {
                    LATEST_VERSION -> "${MIRROR}/war/latest/jenkins.war"
                    LATEST_LTS_VERSION -> "${MIRROR}/war-stable/latest/jenkins.war"
                    else -> {
                        if (!isJenkinsVersion(version)) {
                            throw GradleException("Unsupported Jenkins version '${version}'")
                        }
                        "https://repo.jenkins-ci.org/public/org/jenkins-ci/main/jenkins-war/${version}/jenkins-war-${version}.war"
                    }
                }

            return JenkinsVersion(version, URL(downloadUrl), javaVersion)
        }

        private fun isJenkinsVersion(version: String) = JENKINS_VERSION_PATTERN.matches(version)
    }

    val isDefault: Boolean
        get() = version == V2_375_VERSION

    val label: String
        get() = if (isJenkinsVersion(version)) {
            version.replace(".", "_")
        } else {
            version.split("-").joinToString(separator = "") { it.capitalized() }
        }

    val requiredJavaVersion: JavaVersion
        get() = JavaVersion.toVersion(javaVersion.toString())
}
