plugins {
    id("com.gradle.develocity") version "3.19.2"
    id("com.gradle.common-custom-user-data-gradle-plugin") version "2.1"
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

includeBuild("build-logic")

include("acceptance-tests")
include("configuration-maven-extension")

rootProject.name = "gradle-plugin"

val gradleExt = (gradle as ExtensionAware).extra

val ciJenkinsBuild by gradleExt { System.getenv("JENKINS_URL") != null }
val ciTeamCityBuild by gradleExt { System.getenv("TEAMCITY_VERSION") != null }
val isCi by gradleExt { ciJenkinsBuild || ciTeamCityBuild }

develocity {
    projectId = "jenkinsci-gradle-plugin"
    buildScan {
        uploadInBackground = !isCi
        termsOfUseUrl = "https://gradle.com/terms-of-service"
        termsOfUseAgree = "yes"
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
        remote(develocity.buildCache) {
            isEnabled = true
            val accessKey = System.getenv("GRADLE_ENTERPRISE_ACCESS_KEY")
            isPush = !accessKey.isNullOrEmpty()
            server = System.getenv("GRADLE_CACHE_REMOTE_URL")
        }
    }
}
