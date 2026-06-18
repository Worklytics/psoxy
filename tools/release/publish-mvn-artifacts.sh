#!/bin/bash

COLORSCHEME_SH="$(dirname "$0")/../set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

# shellcheck source=lib/delete-gh-maven-packages.sh
source "$(dirname "$0")/lib/delete-gh-maven-packages.sh"

PATH_TO_REPO="$1"

if [ -z "$PATH_TO_REPO" ]; then
  printf "${ERR}Error: PATH_TO_REPO is required.${NC}\n"
  exit 1
fi

# if PATH_TO_REPO is not blank, but does not end with a slash, add one
if [[ "$PATH_TO_REPO" != */ ]]; then
  PATH_TO_REPO="$PATH_TO_REPO/"
fi

if [ ! -f "${PATH_TO_REPO}java/pom.xml" ]; then
  printf "${ERR}${PATH_TO_REPO}java/pom.xml not found. set <path-to-repo> argument to point to the root of a psoxy checkout. Exiting.${NC}\n"
  exit 1
fi

printf "${WARN}WARNING: this is not recommended; use ${INFO}gh run publish-release-artifacts.yaml --ref <version>${NC}${WARN} instead. That will be a more reliable fresh build.${NC}\n"

# 1. Branch/Tag Validation
CURRENT_BRANCH=$(git -C "$PATH_TO_REPO" branch --show-current)
if [ -z "$CURRENT_BRANCH" ]; then
  CURRENT_TAG=$(git -C "$PATH_TO_REPO" describe --tags --exact-match 2>/dev/null)
fi

IS_RC=false
IS_MAIN=false
IS_TAG=false

if [[ -n "$CURRENT_TAG" && "$CURRENT_TAG" =~ ^v ]]; then
  IS_TAG=true
  printf "Detected release tag: ${INFO}$CURRENT_TAG${NC}\n"
elif [[ "$CURRENT_BRANCH" == "main" ]]; then
  IS_MAIN=true
  printf "Detected main branch.\n"
elif [[ "$CURRENT_BRANCH" =~ ^rc- ]]; then
  IS_RC=true
  printf "Detected RC branch: ${INFO}$CURRENT_BRANCH${NC}\n"
else
  printf "${ERR}Error: This script must be run from 'main', an 'rc-*' branch, or a 'v*' tag.${NC}\n"
  exit 1
fi

cd "${PATH_TO_REPO}java"

# 2. Determine Version
POM_VERSION=$(mvn help:evaluate -Dexpression=revision -q -DforceStdout)

if [ "$IS_RC" = true ]; then
  BASENAME=${CURRENT_BRANCH#rc-}
  VERSION_NUM=${BASENAME#v}
  TARGET_VERSION="${VERSION_NUM}-SNAPSHOT"

  printf "RC build detected. Using version: ${INFO}${TARGET_VERSION}${NC}\n"

  # 3. Delete existing SNAPSHOT artifacts
  delete_gh_maven_package_versions "$PATH_TO_REPO" "$TARGET_VERSION"

else
  TARGET_VERSION="${POM_VERSION}"
  printf "Standard build. Using version: ${INFO}${TARGET_VERSION}${NC}\n"
fi

printf "\nPublishing Maven artifacts to GitHub Packages ...\n"
printf "  (requires GitHub token with ${INFO}write:packages${NC} permission in ${INFO}~/.m2/settings.xml${NC})\n"

if mvn clean deploy -Drevision="${TARGET_VERSION}" -DskipTests; then
  printf "${SUCCESS}✓${NC} Maven artifacts published to GitHub Packages\n"
else
  printf "${ERR}✗${NC} Maven deploy failed. You may need to configure authentication in ~/.m2/settings.xml\n"
  printf "  See: https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry\n"
  exit 1
fi

if [ "$IS_TAG" = true ]; then
  SNAPSHOT_VERSION="${TARGET_VERSION}-SNAPSHOT"
  printf "\nRelease published. Removing RC SNAPSHOT artifacts (${INFO}${SNAPSHOT_VERSION}${NC}) from GitHub Packages...\n"
  delete_gh_maven_package_versions "$PATH_TO_REPO" "$SNAPSHOT_VERSION"
fi
