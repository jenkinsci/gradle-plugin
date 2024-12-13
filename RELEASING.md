These are the steps to release the Gradle Plugin, which is a gradle-based
Jenkins plugin not using a release plugin.

* Ensure you have the latest code from origin: `git pull origin`
* Make sure tests still run: `./gradlew test`
* Run locally to perform sanity check: `./gradlew server`
* Edit _gradle.properties_ to strip `-SNAPSHOT` from version: `vi gradle.properties`
* Ensure everything is checked in: `git commit -S -am "Releasing 1.25"`
* Ensure you have your Jenkins credentials in _~/.jenkins-ci.org_: `cat ~/.jenkins-ci.org`
```
userName=yourUsername
password=IHeartJenkins
```
* Deploy: `./gradlew clean publish`
* Tag the source as it is: `git tag -s -a gradle-1.25 -m "Staging 1.25"`
* Increment the version in _gradle.properties_ and append `-SNAPSHOT`: `echo "version=1.26-SNAPSHOT">gradle.properties`
* Commit the updated version number: `git commit -S -am "Bumping to next rev"`
* Push the two new commit and the tag back to GitHub: `git push --tags && git push`
* Review and publish the release notes draft on Github: https://github.com/jenkinsci/gradle-plugin/releases
* Close all resolved issues in [JIRA](https://issues.jenkins-ci.org/browse/JENKINS-33357?jql=status%20%3D%20Resolved%20AND%20component%20%3D%20gradle-plugin)
* Wait up to twelve hours for it show up in the Update Center
* Follow the [@jenkins_release](https://twitter.com/jenkins_release) Twitter account and retweet the release!
