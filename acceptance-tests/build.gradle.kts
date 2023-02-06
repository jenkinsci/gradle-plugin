import de.undercouch.gradle.tasks.download.Download
import org.gradle.internal.os.OperatingSystem
import java.net.URL

plugins {
    java
    id("de.undercouch.download") version "5.3.1"
}

val athVersion = "5478.vb_b_cd04943676"
val ciTeamCityBuild: Boolean by (gradle as ExtensionAware).extra
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

    implementation("org.jenkins-ci:acceptance-test-harness:${athVersion}")

    testImplementation(platform("io.netty:netty-bom:4.1.87.Final"))
    testImplementation("io.ratpack:ratpack-test:1.9.0")

    add(gradlePlugin.name, project(path = ":", configuration = "gradlePluginJpi"))
}

val currentJava = JavaVersion.current()

val jenkinsVersions = listOf(
    JenkinsVersion.LATEST,
    JenkinsVersion.LATEST_LTS,
    JenkinsVersion.V2_356
)

val allTestTasks =
    jenkinsVersions
        .filter { jenkinsVersion ->
            jenkinsVersion.isDefault || currentJava.isCompatibleWith(jenkinsVersion.requiredJavaVersion)
        }
        .map { jenkinsVersion ->
            val downloadJenkinsTask =
                tasks.register<Download>("downloadJenkins${jenkinsVersion.label}") {
                    src(jenkinsVersion.downloadUrl)
                    dest(file("${project.gradle.gradleUserHomeDir}/jenkins-cache/${jenkinsVersion.version}/jenkins.war"))
                    onlyIfModified(true)
                    tempAndMove(true)
                }

            val testTask =
                if (jenkinsVersion.isDefault) {
                    tasks.named<Test>("test")
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

                jvmArgumentProviders.add(ChromeDriverProvider(ciTeamCityBuild))

                doFirst {
                    environment(mapOf(
                        "JENKINS_WAR" to downloadJenkinsTask.get().outputs.files.singleFile,
                        "LOCAL_JARS" to gradlePlugin.singleFile,
                        "BROWSER" to if (ciJenkinsBuild) "firefox-container" else "chrome"
                    ))
                }
            }

            testTask
        }

val testAllTask = tasks.register("testAll") {
    dependsOn(allTestTasks)
}

tasks.named("check").configure {
    dependsOn(testAllTask)
}

data class JenkinsVersion(val version: String, val downloadUrl: URL, val javaVersion: JavaLanguageVersion) {

    companion object {

        private const val LATEST_VERSION = "latest"
        private const val LATEST_LTS_VERSION = "latest-lts"
        private const val V2_356_VERSION = "2.356"

        private const val MIRROR = "https://get.jenkins.io"

        private val JENKINS_VERSION_PATTERN = "^\\d+([.]\\d+)*?\$".toRegex()

        private val JAVA_11 = JavaLanguageVersion.of(11)

        val LATEST = of(LATEST_VERSION)
        val LATEST_LTS = of(LATEST_LTS_VERSION)
        val V2_356 = of(V2_356_VERSION)

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
        get() = version == V2_356_VERSION

    val label: String
        get() = if (isJenkinsVersion(version)) {
            version.replace(".", "_")
        } else {
            version.split("-").joinToString(separator = "") { it.capitalize() }
        }

    val requiredJavaVersion: JavaVersion
        get() = JavaVersion.toVersion(javaVersion.toString())
}

class ChromeDriverProvider(private val teamCityBuild: Boolean) : CommandLineArgumentProvider {

    override fun asArguments(): Iterable<String> =
        // If executed on TeamCity, we need to set the Chromedriver path
        if (teamCityBuild) {
            listOf("-Dwebdriver.chrome.driver=${System.getenv("HOME")}/.gradle/webdriver/chromedriver/chromedriver")
        } else {
            emptyList()
        }
}
