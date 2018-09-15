#!/bin/bash
echo "Importing keys..."
openssl aes-256-cbc -K $encrypted_323bbacbf530_key -iv $encrypted_323bbacbf530_iv -in codesigning.asc.enc -out codesigning.asc -d
gpg --fast-import codesigning.asc