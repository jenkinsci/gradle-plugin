package hudson.plugins.gradle.injection

class MavenSnippets {
    static String simplePom(String extra = '') {
        """<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>hudson.plugins.gradle</groupId>
  <artifactId>maven-build-scan</artifactId>
  <packaging>jar</packaging>
  <version>1.0</version>

  <properties>
    <maven.compiler.source>1.8</maven.compiler.source>
    <maven.compiler.target>1.8</maven.compiler.target>
  </properties>
  ${extra}
</project>"""
    }

    static String httpsPluginRepositories() {
        '''<pluginRepositories>
                <pluginRepository>
                    <id>central</id>
                    <name>Central Repository</name>
                    <url>https://repo.maven.apache.org/maven2</url>
                </pluginRepository>
            </pluginRepositories>'''
    }

    static String buildScanExtensions() {
        '''<?xml version="1.0" encoding="UTF-8"?>
<extensions>
    <extension>
        <groupId>com.gradle</groupId>
        <artifactId>develocity-maven-extension</artifactId>
        <version>2.0</version>
    </extension>
</extensions>
'''
    }

    static String develocityConfiguration() {
        '''<develocity
    xmlns="https://www.gradle.com/gradle-enterprise-maven" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="https://www.gradle.com/gradle-enterprise-maven https://www.gradle.com/schema/gradle-enterprise-maven.xsd">
  <buildScan>
    <publishing>
      <onlyIf>true</onlyIf>
    </publishing>
    <backgroundBuildScanUpload>false</backgroundBuildScanUpload>
    <termsOfUse>
      <url>https://gradle.com/help/legal-terms-of-use</url>
      <accept>true</accept>
    </termsOfUse>
  </buildScan>
</develocity>
'''
    }

}
