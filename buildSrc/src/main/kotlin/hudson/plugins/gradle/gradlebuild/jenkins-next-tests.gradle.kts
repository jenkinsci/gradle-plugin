package hudson.plugins.gradle.gradlebuild

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel

val test by java.sourceSets

val main by java.sourceSets

val jenkinsNextTest by java.sourceSets.creating {
    extendsFrom(test, configurations)
    compileClasspath += main.output
    runtimeClasspath += main.output
}

val jenkinsTestNextTestTask = tasks.register<Test>("jenkinsNextTest") {
    description = "Runs tests which require a newer Jenkins version (requires Jenkins >=2.60.3)."
    testClassesDirs = jenkinsNextTest.output.classesDirs
    classpath = jenkinsNextTest.runtimeClasspath
}

markAsTestSourceSet(jenkinsNextTest)

defaultTasks.add(jenkinsTestNextTestTask.name)

fun Project.markAsTestSourceSet(sourceSet: SourceSet) {
    plugins.withType<IdeaPlugin> {
        configure<IdeaModel> {
            module {
                testSourceDirs = testSourceDirs + sourceSet.java.srcDirs
                testSourceDirs = testSourceDirs + sourceSet.groovy.srcDirs
                testResourceDirs = testResourceDirs + sourceSet.resources.srcDirs
            }
        }
    }
}