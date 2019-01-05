plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

dependencies {
    compile(gradleApi())
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}