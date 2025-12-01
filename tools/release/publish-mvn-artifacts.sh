#!/bin/bash

GREEN='\e[0;32m'
RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

PATH_TO_REPO="$1"

if [ -z "$PATH_TO_REPO" ]; then
  printf "${RED}Error: PATH_TO_REPO is required.${NC}\n"
  exit 1
fi

if [ ! -f "${PATH_TO_REPO}java/pom.xml" ]; then
  printf "${RED}${PATH_TO_REPO}java/pom.xml not found. set <path-to-repo> argument to point to the root of a psoxy checkout. Exiting.${NC}\n"
  exit 1
fi

printf "\nPublishing Maven artifacts to GitHub Packages ...\n"
printf "  (requires GitHub token with ${BLUE}write:packages${NC} permission in ${BLUE}~/.m2/settings.xml${NC})\n"

cd "${PATH_TO_REPO}java"
if mvn clean deploy -DskipTests; then
  printf "${GREEN}✓${NC} Maven artifacts published to GitHub Packages\n"
else
  printf "${RED}✗${NC} Maven deploy failed. You may need to configure authentication in ~/.m2/settings.xml\n"
  printf "  See: https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry\n"
fi