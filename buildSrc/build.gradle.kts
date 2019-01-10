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

plugins.withType<KotlinDslPlugin> {
    kotlinDslPluginOptions {
        experimentalWarning.set(false)
    }
}
