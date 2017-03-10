Jenkins Gradle Plugin
=====================

[![Build Status](https://ci.jenkins.io/buildStatus/icon?job=Plugins/gradle-plugin/master)](https://ci.jenkins.io/blue/organizations/jenkins/Plugins%2Fgradle-plugin/branches/)

This plugin allows Jenkins to invoke [Gradle](href="http://www.gradle.org/) build scripts directly.
For more information see the [Jenkins wiki page](http://wiki.jenkins-ci.org/display/JENKINS/Gradle+Plugin).

In order to release this plugin have a look at [here](RELEASING.md).

## Release Notes
* 1.27 (unreleased)
  * Job parameters are now correctly quoted when passed as system properties [JENKINS-42573](https://issues.jenkins-ci.org/browse/JENKINS-42573)
  * Make finding wrapper location more robust on Windows
  * Increase required core version to 1.642.1
* 1.26 (Feb 13 2016)
  * Use `@DataBoundSetter` instead of a (too) large `@DataBoundConstructor`
  * Add @Symbol annotations for step and tool [JENKINS-37394](https://issues.jenkins-ci.org/browse/JENKINS-37394)
  * Make it possible to configure the wrapper location [JENKINS-35029](https://issues.jenkins-ci.org/browse/JENKINS-35029)
  * Update icon for build scan integration
  * Remove description from build step