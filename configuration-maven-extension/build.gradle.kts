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

repositories {
    mavenCentral()
}

configurations {
    create("mvnExtension") {
        isCanBeConsumed = true
        isCanBeResolved = false
    }
}

dependencies {
    compileOnly("org.apache.maven:maven-core:3.8.6")
    compileOnly("com.gradle:gradle-enterprise-maven-extension:${gradle.gradleEnterpriseMavenExtensionVersion}")
}

val jar by tasks.getting(Jar::class)

artifacts {
    add("mvnExtension", jar)
}

val Gradle.gradleEnterpriseMavenExtensionVersion: String
    get() = (gradle as ExtensionAware).extra["gradleEnterpriseMavenExtensionVersion"] as String
