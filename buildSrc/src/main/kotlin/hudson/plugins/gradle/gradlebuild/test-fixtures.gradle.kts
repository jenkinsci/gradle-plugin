package hudson.plugins.gradle.gradlebuild

import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.*
import java.util.Locale

val main by java.sourceSets

val testFixtures by java.sourceSets.creating {
    extendsFrom(main, configurations)
    compileClasspath += main.output
}

java.sourceSets.named("test", SourceSet::class) {
    extendsFrom(testFixtures, configurations)
}

java.sourceSets.matching { it.name.toLowerCase(Locale.ROOT).endsWith("test") }.all {
    compileClasspath += testFixtures.output
    runtimeClasspath += testFixtures.output
}
    