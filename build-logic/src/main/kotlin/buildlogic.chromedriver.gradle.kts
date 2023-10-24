import org.gradle.api.internal.artifacts.transform.UnzipTransform
import org.gradle.internal.os.OperatingSystem

// Latest version: https://chromedriver.storage.googleapis.com/LATEST_RELEASE
val chromeDriverVersion = "117.0.5938.88"
val ciTeamCityBuild: Boolean by (gradle as ExtensionAware).extra

val os: OperatingSystem = OperatingSystem.current()
val driverOsFilenamePart = when {
    os.isWindows -> "win32"
    os.isMacOsX && os.nativePrefix.contains("aarch64") -> "mac_arm64"
    os.isMacOsX -> "mac64"
    os.isLinux && os.nativePrefix.contains("64") -> "linux64"
    else -> "linux32"
}

repositories {
    exclusiveContent {
        forRepository {
            ivy {
                url = uri("https://edgedl.me.gvt1.com/edgedl/chrome/chrome-for-testing/")
                patternLayout {
                    artifact("[revision]/[classifier]/[artifact]-[classifier].[ext]")
                }
                metadataSources {
                    artifact()
                }
            }
        }
        filter {
            includeModule("chromedriver", "chromedriver")
        }
    }
}

val chromedriver: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.DIRECTORY_TYPE)
}

dependencies {
    registerTransform(UnzipTransform::class) {
        from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.ZIP_TYPE)
        to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.DIRECTORY_TYPE)
    }
    chromedriver("chromedriver:chromedriver:${chromeDriverVersion}:${driverOsFilenamePart}@zip")
}

// We do not run acceptance-tests on Windows
if (ciTeamCityBuild && !os.isWindows) {
    val killRunningChromedriverInstances by tasks.registering(Exec::class) {
        val echoOption = if (os.isLinux) "e" else "l"
        commandLine("bash", "-c", "pkill -9 -${echoOption} chrome")
        isIgnoreExitValue = true
    }

    tasks.withType(Test::class).configureEach {
        inputs.files(chromedriver)
        jvmArgumentProviders += ChromeDriverProvider(chromedriver)

        finalizedBy(killRunningChromedriverInstances)
    }
}

class ChromeDriverProvider(@Internal val chromedriverDirectory: FileCollection) : CommandLineArgumentProvider {

    override fun asArguments(): Iterable<String> {
        val chromedriver = chromedriverDirectory.asFileTree.files.first { it.name == "chromedriver" }
        // UnzipTransform does not preserve file permissions, so we're restoring it here
        chromedriver.setExecutable(true)
        return listOf("-Dwebdriver.chrome.driver=$chromedriver")
    }
}
