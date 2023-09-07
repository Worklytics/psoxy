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

git fetch origin

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
  printf "Tagging ${GREEN}$RELEASE${NC} ...\n"
  git tag $RELEASE
fi

printf "Pushing tag ${GREEN}$RELEASE${NC} to origin ...\n"
git push origin $RELEASE

if gh release view $RELEASE >/dev/null 2>&1
then
  printf "Release ${GREEN}$RELEASE${NC} already exists.\n"
else
  printf "Creating release ${GREEN}$RELEASE${NC} in GitHub ...\n"
  gh release create --draft --generate-notes $RELEASE
fi

printf "Updating release notes for ${GREEN}$RELEASE${NC} ...\n"
# Fetch release notes
release_notes=$(gh release view $RELEASE --json body -q '.body')

# Remove GitHub username mentions
modified_notes=$(echo -e "$release_notes" | sed -r 's/by @[a-zA-Z0-9_-]+ in//g')

# Update release notes
gh release edit $RELEASE -n "$modified_notes"


printf "Delete ${BLUE}rc-${RELEASE}${NC} tag?\n"
read -p "(Y/n) " -n 1 -r
REPLY=${REPLY:-Y}
echo    # Move to a new line
case "$REPLY" in
  [yY][eE][sS]|[yY])
     git branch -d "rc-$RELEASE"
    ;;
  *)
    printf "Skipped deletion of ${BLUE}rc-$RELEASE${NC}\n"
    ;;
esac
