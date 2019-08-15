clean:
	rm -rf .jenkins

jfr-test: clean
	mkdir .jenkins
	cd .jenkins && git clone https://github.com/oleg-nenashev/pipeline-library.git
	cd .jenkins/pipeline-library && git checkout jfr-gradle
	docker run --rm -v maven-repo:/root/.m2 \
	    -v $(CURDIR):/workspace/ \
		-v $(CURDIR)/.jenkins/pipeline-library:/var/jenkins_home/pipeline-library \
	    onenashev/ci.jenkins.io-runner
