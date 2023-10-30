plugins {
    id("com.gradle.enterprise") version "3.15.1"
    id("com.gradle.common-custom-user-data-gradle-plugin") version "1.12"
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0"
}

includeBuild("build-logic")

include("acceptance-tests")
include("configuration-maven-extension")

rootProject.name = "gradle-plugin"

if (!JavaVersion.current().isJava11) {
    throw GradleException("Build requires Java 11")
}

val gradleExt = (gradle as ExtensionAware).extra

val ciJenkinsBuild by gradleExt { System.getenv("JENKINS_URL") != null }
val ciTeamCityBuild by gradleExt { System.getenv("TEAMCITY_VERSION") != null }
val isCi by gradleExt { ciJenkinsBuild || ciTeamCityBuild }
val gradleEnterpriseMavenExtensionVersion by gradleExt { "1.19.3" }
val commonCustomUserDataMavenExtensionVersion by gradleExt { "1.12.4" }

gradleEnterprise {
    projectId = "jenkinsci-gradle-plugin"
    buildScan {
        capture { isTaskInputFiles = true }
        isUploadInBackground = !isCi
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        publishAlways()
        obfuscation {
            ipAddresses { addresses -> addresses.map { _ -> "0.0.0.0" } }
        }
    }
}

buildCache {
    local {
        isEnabled = true
    }
    if (ciTeamCityBuild) {
        remote(gradleEnterprise.buildCache) {
            isEnabled = true
            isPush = true
            server = System.getenv("GRADLE_CACHE_REMOTE_URL")
        }
    }
}
