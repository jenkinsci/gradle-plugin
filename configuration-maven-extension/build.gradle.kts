import java.net.URI

plugins {
    java
    id("buildlogic.reproducible-archives")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

group = "com.gradle"
description = "Maven extension to configure injected Gradle Enterprise Maven extension"
version = "1.0.0"

val develocityMavenExtensionVersion: String by (gradle as ExtensionAware).extra

// only necessary as long as we use a rc
repositories {
    mavenCentral()
    maven {
        url = URI.create("https://repo.grdev.net/artifactory/public")
    }
}

val mvnExtension: Configuration by configurations.creating { isCanBeConsumed = true; isCanBeResolved = false }

dependencies {
    compileOnly("org.apache.maven:maven-core:3.8.7")
    compileOnly("com.gradle:develocity-maven-extension:${develocityMavenExtensionVersion}")
}

val jar by tasks.getting(Jar::class)

artifacts {
    add(mvnExtension.name, jar)
}
