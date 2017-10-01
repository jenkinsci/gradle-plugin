Jenkins Gradle Plugin
=====================

[![Build Status](https://ci.jenkins.io/buildStatus/icon?job=Plugins/gradle-plugin/master)](https://ci.jenkins.io/blue/organizations/jenkins/Plugins%2Fgradle-plugin/branches/)

This plugin allows Jenkins to invoke [Gradle](href="http://www.gradle.org/) build scripts directly.
For more information see the [Jenkins wiki page](http://wiki.jenkins-ci.org/display/JENKINS/Gradle+Plugin).

In order to release this plugin have a look at [here](RELEASING.md).

## Release Notes
* 1.28 (unreleased)
  * Empty job parameters are passed as empty ([JENKINS-45300](https://issues.jenkins-ci.org/browse/JENKINS-45300))
  * Console annotator endless loop in combination with using the Ant plugin fixed ([JENKINS-46051](https://issues.jenkins-ci.org/browse/JENKINS-46051))
* 1.27.1 (Jul 1 2017)
  * Increase required core version to 1.642.1
  * Make finding wrapper location more robust on Windows
  * Job parameters are now correctly quoted when passed as system properties ([JENKINS-42573](https://issues.jenkins-ci.org/browse/JENKINS-42573) and [JENKINS-20505](https://issues.jenkins-ci.org/browse/JENKINS-20505))
  * Do not pass all job parameters as (system) properties to Gradle by default
  * Include automated test for CLI command [JENKINS-42847](https://issues.jenkins-ci.org/browse/JENKINS-42847)
  * Ensure that Gradle's bin directory is on the path for Pipeline tool steps [JENKINS-42381](https://issues.jenkins-ci.org/browse/JENKINS-42381)
  * Add option to pass only selected system properties to Gradle
  * Add option to pass only selected project properties to Gradle
  * Progress status `FROM-CACHE` and `NO-SOURCE` are highlighted in the console, too.
  * Support build scan plugin 1.8
* 1.27 (Jun 23 2017)
  * DO NOT USE - PROBLEMS WITH RELEASING [JENKINS-45126](https://issues.jenkins-ci.org/browse/JENKINS-45126)
* 1.26 (Feb 13 2016)
  * Use `@DataBoundSetter` instead of a (too) large `@DataBoundConstructor`
  * Add @Symbol annotations for step and tool [JENKINS-37394](https://issues.jenkins-ci.org/browse/JENKINS-37394)
  * Make it possible to configure the wrapper location [JENKINS-35029](https://issues.jenkins-ci.org/browse/JENKINS-35029)
  * Update icon for build scan integration
  * Remove description from build step
