import com.github.spotbugs.snom.SpotBugsTask
import com.gradle.enterprise.gradleplugin.testretry.retry
import org.jenkinsci.gradle.plugins.jpi.JpiLicense
import org.jenkinsci.gradle.plugins.jpi.deployment.CreateVersionlessLookupTask
import org.jenkinsci.gradle.plugins.jpi.localization.LocalizationTask
import org.jenkinsci.gradle.plugins.manifest.GenerateJenkinsManifestTask
import java.util.zip.ZipFile

plugins {
    id("org.jenkins-ci.jpi") version "0.51.0"
    id("com.github.spotbugs") version "6.0.24"
    id("codenarc")
    id("buildlogic.reproducible-archives")
}

group = "org.jenkins-ci.plugins"
description = "This plugin adds Gradle support to Jenkins"

val coreBaseVersion = "2.401"
val corePatchVersion = "3"
val coreBomVersion = "2745.vc7b_fe4c876fa_"

val gradleExt = (gradle as ExtensionAware).extra

val ciJenkinsBuild: Boolean by gradleExt
val isCi: Boolean by gradleExt

repositories {
    mavenCentral()
}

jenkinsPlugin {
    // Version of Jenkins core this plugin depends on.
    jenkinsVersion.set("${coreBaseVersion}.${corePatchVersion}")

    // Human-readable name of plugin.
    displayName = "Gradle Plugin"

    // URL for plugin on Jenkins wiki or elsewhere.
    url = "https://github.com/jenkinsci/gradle-plugin"

    // Plugin URL on GitHub. Optional.
    gitHubUrl = "https://github.com/jenkinsci/gradle-plugin"

    // Plugin ID, defaults to the project name without trailing '-plugin'
    shortName = "gradle"

    fileExtension = "hpi"

    compatibleSinceVersion = "1.0"

    developers {
        developer {
            id.set("wolfs")
            name.set("Stefan Wolf")
        }
    }

    // TODO: Refactor
    licenses.license(delegateClosureOf<JpiLicense> {
        setProperty("name", "MIT License")
        setProperty("distribution", "repo")
        setProperty("url", "https://opensource.org/licenses/MIT")
    })

    generateTests.set(true)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }

    registerFeature("optionalPlugin") {
        usingSourceSet(sourceSets["main"])
    }
}

tasks.compileJava {
    options.release = 11
}

// see https://github.com/jenkinsci/gradle-jpi-plugin#customizing-further
tasks.server.configure {
    execSpec {
        classpath(layout.buildDirectory.dir("resources/test"))
    }
}

val includedLibs: Configuration by configurations.creating
val gradlePluginJpi: Configuration by configurations.creating { isCanBeConsumed = true; isCanBeResolved = false }

dependencies {
    api(platform("io.jenkins.tools.bom:bom-${coreBaseVersion}.x:${coreBomVersion}"))

    implementation("org.jenkins-ci.plugins:jackson2-api")
    implementation("org.jenkins-ci.plugins:structs")
    implementation("org.jenkins-ci.plugins.workflow:workflow-api")
    implementation("org.jenkins-ci.plugins.workflow:workflow-cps")
    implementation("org.jenkins-ci.plugins.workflow:workflow-job")
    implementation("org.jenkins-ci.plugins.workflow:workflow-basic-steps")
    implementation("org.jenkins-ci.plugins.workflow:workflow-durable-task-step")
    implementation("org.jenkins-ci.plugins.workflow:workflow-step-api")
    implementation("org.jenkins-ci.plugins:credentials")
    implementation("org.jenkins-ci.plugins:plain-credentials")
    implementation("io.jenkins.plugins:okhttp-api")

    "optionalPluginImplementation"("org.jenkins-ci.main:maven-plugin:3.23") {
        because("Lowest version that works with our dependencies")
        // conflicts with Jenkins bom bringing Guice 6.0.0
        exclude(group = "com.google.inject", module = "guice")
    }

    // Higher versions fail in our tests with ClassNotFoundException during SCM initialization unless Jenkins is updated
    "optionalPluginImplementation"("org.jenkins-ci.plugins:git:4.9.4") {
        because("VCS repositories filtering is supported for Develocity auto-injection")
    }

    implementation("commons-validator:commons-validator:1.9.0") {
        exclude(group = "commons-beanutils", module = "commons-beanutils")
        exclude(group = "commons-logging", module = "commons-logging")
    }

    add(includedLibs.name, project(path = ":configuration-maven-extension", configuration = "mvnExtension"))

    testImplementation("org.jenkins-ci.main:jenkins-test-harness:2225.v04fa_3929c9b_5")
    testImplementation("org.jenkins-ci.main:jenkins-test-harness-tools:2.2")
    testImplementation("io.jenkins:configuration-as-code:1.4")
    testImplementation("org.jenkins-ci.plugins:timestamper")
    testImplementation("org.jenkins-ci.plugins:pipeline-stage-step")
    testImplementation("org.jenkins-ci.plugins:pipeline-maven:3.10.0")
    testImplementation("org.spockframework:spock-core:2.3-groovy-2.5")
    testImplementation("org.spockframework:spock-junit4:2.3-groovy-2.5")
    testImplementation("net.bytebuddy:byte-buddy:1.15.3")
    testImplementation("org.objenesis:objenesis:3.4")
    testImplementation(platform("io.netty:netty-bom:4.1.114.Final"))

    testImplementation("io.ratpack:ratpack-groovy-test:1.9.0") {
        exclude(group = "com.google.guava", module = "guava")
    }
    testImplementation("com.google.guava:guava:19.0") {
        because("Lowest possible version that works with Jenkins and Ratpack")
    }

    testRuntimeOnly("org.jenkins-ci.main:jenkins-war:${coreBaseVersion}")

    jenkinsServer("org.jenkins-ci.plugins:git")

    components {
        // dom4j brings a bogus xml parser (pull-parser) that takes over the jdk one
        // see https://github.com/dom4j/dom4j/issues/99
        withModule<ClearDependencies>("org.dom4j:dom4j")
    }
}

class ClearDependencies : ComponentMetadataRule {
    override fun execute(t: ComponentMetadataContext) {
        t.details.allVariants { withDependencies { clear() } }
    }
}


spotbugs {
    toolVersion.set("4.7.2")
    excludeFilter.set(file("spotbugs-exclude.xml"))
}

tasks.withType<SpotBugsTask>().configureEach {
    reports.maybeCreate("xml").required.set(true)
    reports.maybeCreate("html").required.set(true)
}

tasks.spotbugsTest {
    isEnabled = false
}

val main: SourceSet by sourceSets.getting

val test: SourceSet by sourceSets.getting

codenarc {
    toolVersion = "1.5"
    sourceSets = listOf(test)
}

tasks.test {
    systemProperties(
        mapOf(
            "hudson.model.DownloadService.noSignatureCheck" to true,
            "jenkins.test.timeout" to 300, // override default timeout
            "jdk8.home" to javaToolchains.compilerFor {
                languageVersion = JavaLanguageVersion.of(8)
            }.get().metadata.installationPath.toString()
        )
    )
    ignoreFailures = ciJenkinsBuild
    maxParallelForks = findProperty("maxParallelForks") as Int? ?: 3
    retry {
        if (isCi) {
            maxRetries.set(2)
            maxFailures.set(5)
        }
        failOnPassedAfterRetry.set(false)
    }
    useJUnitPlatform()
}

// See the original configuration: https://github.com/jenkinsci/gradle-jpi-plugin/blob/v0.47.0/src/main/kotlin/org/jenkinsci/gradle/plugins/testing/JpiTestingPlugin.kt#L79
tasks.named<CreateVersionlessLookupTask>("createVersionlessLookup") {
    val jpiAllPlugins = project.configurations.named("jpiAllPlugins")
    val runtimeClasspathJenkins = project.configurations.named("runtimeClasspathJenkins")

    allResolvedPlugins.from(jpiAllPlugins, runtimeClasspathJenkins)
    moduleVersionToModule.set(project.provider {
        val artifacts =
            jpiAllPlugins.get().resolvedConfiguration.resolvedArtifacts +
                runtimeClasspathJenkins.get().resolvedConfiguration.resolvedArtifacts

        artifacts.associate { it.file.name to "${it.name}.jpi" }
    })
}

tasks.named<GenerateJenkinsManifestTask>("generateJenkinsManifest").configure {
    dynamicSnapshotVersion.set(false)
}

fun checkArchiveManifest(archive: File) {
    ZipFile(archive).use { zip ->
        zip.getInputStream(zip.getEntry("META-INF/MANIFEST.MF")).bufferedReader().use {
            check(it.readText().contains("Plugin-Version: ${project.version}")) {
                "Wrong metadata in file $archive - run a clean build"
            }
        }
    }
}

tasks.withType<AbstractArchiveTask> {
    inputs.property("pluginVersion") {
        project.version
    }
}

val jar by tasks.getting(Jar::class)
val jpi by tasks.getting(War::class)

val checkArchiveManifests: Task by tasks.creating {
    dependsOn(jar, jpi)
    doLast {
        checkArchiveManifest(jar.archiveFile.get().asFile)
        checkArchiveManifest(jpi.archiveFile.get().asFile)
    }
}

tasks.withType<AbstractPublishToMaven> {
    dependsOn(checkArchiveManifests)
}

defaultTasks.add("test")
defaultTasks.add("jpi")

val localizeMessages: Task by tasks.getting(LocalizationTask::class) {
    outputDir.set(layout.buildDirectory.file("generated/sources/localizeMessages/java/main").map { it.asFile })
}

sourceSets.main {
    java.srcDirs(localizeMessages)
}

val createWrapperZip by tasks.creating(Zip::class) {
    archiveFileName.set("wrapper.zip")
    destinationDirectory.set(File(test.output.resourcesDir, "gradle"))

    from(project.rootDir) {
        include("gradle/**")
        include("gradlew*")
    }
}

tasks.processTestResources {
    dependsOn(createWrapperZip)

    from(includedLibs) {
        into("hudson/plugins/gradle/injection")
    }
}

tasks.jar {
    from(includedLibs) {
        into("hudson/plugins/gradle/injection")
    }
}

artifacts {
    add(gradlePluginJpi.name, jpi)
}
