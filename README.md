Jenkins Gradle Plugin
=====================

This plugin allows Jenkins to invoke [Gradle](href="http://www.gradle.org/) build scripts directly.
For more information see the [Jenkins wiki page](http://wiki.jenkins-ci.org/display/JENKINS/Gradle+Plugin).

In order to release this plugin have a look at [here](RELEASING.md).

## Release Notes
* 1.26 (unreleased)
  * Use `@DataBoundSetter` instead of a (too) large `@DataBoundConstructor`
  * Add @Symbol annotations for step and tool [JENKINS-37394](https://issues.jenkins-ci.org/browse/JENKINS-37394)
  * Make it possible to configure the wrapper location [JENKINS-35029](https://issues.jenkins-ci.org/browse/JENKINS-35029)
  * Update icon for build scan integration