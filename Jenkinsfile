#!/usr/bin/env groovy

/* Only keep the 10 most recent builds. */
properties([[$class: 'BuildDiscarderProperty',
                strategy: [$class: 'LogRotator', numToKeepStr: '10']]])

node('docker') {
  /* Make sure we always have a clean workspace */
  deleteDir()
  
  stage 'Checkout'
  checkout scm

  stage 'Build'
  /* Call the Gradle build. */
  docker.image('openjdk:7-jdk').inside {
    timeout(60) {
      sh './gradlew -i build'
    }
  }

  /* Save Results. */
  stage 'Results'
  /* Archive the test results */
  junit '**/build/test-results/**/TEST-*.xml'
  /* Archive the build artifacts */
  archiveArtifacts artifacts: 'build/libs/*.hpi'
}
