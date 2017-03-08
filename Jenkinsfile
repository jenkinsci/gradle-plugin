#!/usr/bin/env groovy

/* Only keep the 10 most recent builds. */
properties([[$class: 'BuildDiscarderProperty',
                strategy: [$class: 'LogRotator', numToKeepStr: '10']]])

parallel([
  Linux: {
    node('docker') {
      /* Make sure we always have a clean workspace */
      deleteDir()

      stage('Checkout Linux') {
        checkout scm
      }

      stage('Build Linux') {
        /* Call the Gradle build. */
        docker.image('openjdk:7-jdk').inside {
          timeout(60) {
            sh './gradlew -i build'
          }
        }
      }
      /* Save Results. */
      stage('Results Linux') {
        /* Archive the test results */
        junit '**/build/test-results/**/TEST-*.xml'
        /* Archive the build artifacts */
        archiveArtifacts artifacts: 'build/libs/*.hpi'
      }
    }
  },
  'Windows': {
    node('windows') {
      /* Make sure we always have a clean workspace */
      deleteDir()

      stage('Checkout Windows') {
        checkout scm
      }

      stage('Build Windows') {
        withJavaEnv {
          /* Call the Gradle build. */
          bat './gradlew.bat -i build'
        }
      }

      /* Save Results. */
      stage('Results Windows') {
        /* Archive the test results */
        junit '**/build/test-results/**/TEST-*.xml'
      }
    }
  }
])

void withJavaEnv(List envVars = [], def body) {
    // The names here are currently hardcoded for my test environment. This needs
    // to be made more flexible.
    // Using the "tool" Workflow call automatically installs those tools on the
    // node.
    String jdktool = tool name: "jdk8", type: 'hudson.model.JDK'

    // Set JAVA_HOME, MAVEN_HOME and special PATH variables for the tools we're
    // using.
    List javaEnv = ["PATH+JDK=${jdktool}/bin", "JAVA_HOME=${jdktool}"]

    // Add any additional environment variables.
    javaEnv.addAll(envVars)

    // Invoke the body closure we're passed within the environment we've created.
    withEnv(javaEnv) {
        body.call()
    }
}