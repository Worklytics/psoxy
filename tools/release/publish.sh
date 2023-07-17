#!/bin/bash

# Arguments: repository name and tag name
RELEASE="$1"

GREEN='\e[0;32m'
RED='\e[0;31m'
NC='\e[0m' # No Color

set -e

if [ -z "$RELEASE" ]; then
  printf "${RED}Please provide a release tag name.${NC}\n"
  printf "Usage: after merged to main, before tagging:\n"
  printf "  ./tools/release/publish.sh <release-tag>\n"
  exit 1
fi

if git rev-parse "$RELEASE" >/dev/null 2>&1; then
  printf "Tag ${GREEN}$RELEASE${NC} already exists.\n"
else
  git checkout main

  # verify on main branch and clean status
  CURRENT_BRANCH=$(git branch --show-current)
  if [ "$CURRENT_BRANCH" != "main" ]; then
    printf "${RED}Current branch is not main. Please checkout main branch and try again.${NC}\n"
    exit 1
  fi

  git tag $RELEASE
fi

git push origin $RELEASE

if gh release view $RELEASE >/dev/null 2>&1
then
  printf "Release ${GREEN}$RELEASE${NC} already exists.\n"
else
  gh release create --draft --generate-notes $RELEASE
fi


# Fetch release notes
release_notes=$(gh release view $RELEASE --json body -q '.body')

# Remove GitHub username mentions
modified_notes=$(echo -e "$release_notes" | sed -r 's/by @[a-zA-Z0-9_-]+ in//g')

# Update release notes
gh release edit $RELEASE -n "$modified_notes"
