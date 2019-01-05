package hudson.plugins.gradle.gradlebuild

import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.*
import java.util.Locale


val Project.java
    get() = the<JavaPluginConvention>()

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

fun SourceSet.extendsFrom(other: SourceSet, configurations: ConfigurationContainer) {
    configurations {
        compileConfigurationName {
            extendsFrom(configurations[other.compileConfigurationName])
        }
        implementationConfigurationName {
            extendsFrom(configurations[other.implementationConfigurationName])
        }
        runtimeConfigurationName {
            extendsFrom(configurations[other.runtimeConfigurationName])
        }
        runtimeOnlyConfigurationName {
            extendsFrom(configurations[other.runtimeOnlyConfigurationName])
        }
    }
}
