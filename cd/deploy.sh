#!/usr/bin/env bash
#if [ "$TRAVIS_BRANCH" == 'master' ] && [ "$TRAVIS_PULL_REQUEST" == 'false' ]; then
        cd ../;
	cd injector;
	./gradlew uploadArchives
	cd ../injector-android;
	./gradlew uploadArchives
#fi
