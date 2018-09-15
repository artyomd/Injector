#!/bin/bash
BRANCH="master"

set -e

if [ "$TRAVIS_PULL_REQUEST" != "false" ]; then
  echo "Skipping deployment: was pull request."
elif [ "$TRAVIS_BRANCH" != "$BRANCH" ]; then
  echo "Skipping deployment: wrong branch. Expected '$BRANCH' but was '$TRAVIS_BRANCH'."
else
  echo "Deploying..."
  cd ../injector;
  ./gradlew uploadArchives
  cd ../injector-android;
  ./gradlew uploadArchives
  echo "Deployed!"
fi
