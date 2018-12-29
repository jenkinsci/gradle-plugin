These are the steps to release the Gradle Plugin, which is a gradle-based
Jenkins plugin not using a release plugin.

* Ensure you have the latest code from origin: _git pull origin_
* Make sure tests still run: _./gradlew test_
* Run locally to perform sanity check: _./gradlew server_
* Edit gradle.properties to strip -SNAPSHOT from version: _vi gradle.properties_
* Set `compatibleSinceVersion` to the new version if deprecated features have been removed
* Update the release notes, set the release date: `* 1.25 (Jul 21 2016)`
* Ensure everything is checked in: _git commit -S -am "Releasing 1.25"_
* Ensure you have your Jenkins credentials in ~/.jenkins-ci.org: _cat ~/.jenkins-ci.org_
```
userName=yourUsername
password=IHeartJenkins
```
* Deploy: _./gradlew clean publish
* Tag the source as it is: _git tag -s -a gradle-1.25 -m "Staging 1.25"_
* Increment the version in gradle.properties and append "-SNAPSHOT": _echo "version=1.26-SNAPSHOT">gradle.properties_
* Update the release notes, add the next version: `* 1.26 (unreleased)`
* Commit the updated version number: _git commit -S -am "Bumping to next rev"_
* Push the two new commit and the tag back to GitHub: _git push --tags && git push_
* Close all resolved issues in [JIRA](https://issues.jenkins-ci.org/browse/JENKINS-33357?jql=status%20%3D%20Resolved%20AND%20component%20%3D%20gradle-plugin)
* Wait up to twelve hours for it show up in the Update Center
* Follow the @jenkins_release twitter account and retweet the release!
