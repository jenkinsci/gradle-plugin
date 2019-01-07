package hudson.plugins.gradle.gradlebuild

import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.GroovySourceSet
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.*


internal
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


val Project.java
    get() = the<JavaPluginConvention>()


val SourceSet.groovy: SourceDirectorySet
    get() = withConvention(GroovySourceSet::class) { groovy }
