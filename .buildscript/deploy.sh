#!/bin/bash
BRANCH="master"

set -e

if [ "$TRAVIS_PULL_REQUEST" != "false" ]; then
  echo "Skipping deployment: was pull request."
elif [ "$TRAVIS_BRANCH" != "$BRANCH" ]; then
  echo "Skipping deployment: wrong branch. Expected '$BRANCH' but was '$TRAVIS_BRANCH'."
else
  echo "Importing keys..."
  openssl aes-256-cbc -K $encrypted_323bbacbf530_key -iv $encrypted_323bbacbf530_iv -in codesigning.asc.enc -out codesigning.asc -d
  gpg --fast-import codesigning.asc
  echo "Deploying..."
  cd ../injector;
  ./gradlew uploadArchives
  cd ../injector-android;
  ./gradlew uploadArchives
  echo "Deployed!"
fi
