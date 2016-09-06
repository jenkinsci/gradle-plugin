#!groovy

/* Only keep the 10 most recent builds. */
properties([[$class: 'BuildDiscarderProperty',
                strategy: [$class: 'LogRotator', numToKeepStr: '10']]])

node {
  stage 'Checkout'
  checkout scm

  stage 'Build'
  /* Call the Gradle build. */
  gradlew "build"

  /* Save Results. */
  stage 'Results'

  /* Archive the test results */
  junit '**/build/test-results/**/TEST-*.xml'
  /* Archive the build artifacts */
  archiveArtifacts artifacts: 'build/lib/*.hpi,build/lib/*.jpi'
}

/* Run Gradle with the appropriate JDK */
void gradlew(def args) {
  /* Get jdk tool. */
  String jdktool = tool name: "jdk7", type: 'hudson.model.JDK'

  /* Set JAVA_HOME, and special PATH variables. */
  List javaEnv = [
    "PATH+JDK=${jdktool}/bin",
    "JAVA_HOME=${jdktool}",
    // Additional variables needed by tests on machines
    // that don't have global git user.name and user.email configured.
    'GIT_COMMITTER_EMAIL=me@hatescake.com',
    'GIT_COMMITTER_NAME=Hates',
    'GIT_AUTHOR_NAME=Cake',
    'GIT_AUTHOR_EMAIL=hates@cake.com',
    'LOGNAME=hatescake'
  ]

  /* Call maven tool with java envVars. */
  withEnv(javaEnv) {
    timeout(time: 60, unit: 'MINUTES') {
      if (isUnix()) {
        sh "./gradlew ${args}"
      } else {
        bat "gradlew.bat ${args}"
      }
    }
  }
}
