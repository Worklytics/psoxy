#!/bin/bash

# Arguments: repository name and tag name
RELEASE="$1"
PATH_TO_REPO="$2"

GREEN='\e[0;32m'
RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color



if [ ! -f "${PATH_TO_REPO}java/pom.xml" ]; then
  printf "${RED}${PATH_TO_REPO}java/pom.xml not found. set <path-to-repo> argument to point to the root of a psoxy checkout. Exiting.${NC}\n"
  exit 1
fi

CURRENT_RELEASE_NUMBER=$(sed -n 's|[[:space:]]*<revision>\(.*\)</revision>|\1|p' "${PATH_TO_REPO}java/pom.xml" )

if [ -z "$RELEASE" ]; then
  RELEASE="v${CURRENT_RELEASE_NUMBER}"
else
  # maintain compatibility with current interface
  if [ "$RELEASE" != "v${CURRENT_RELEASE_NUMBER}" ]; then
    printf "${RED}Release version provided, ${RELEASE}, does not match current release version in pom.xml, ${CURRENT_RELEASE_NUMBER}. Exiting.${NC}\n"
    exit 1
  fi
fi

# die on error
set -e

git fetch origin

if git rev-parse "$RELEASE" >/dev/null 2>&1; then
  printf "Tag ${GREEN}$RELEASE${NC} already exists.\n"
else
  git checkout main

  git pull origin main

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

# check if rc branch exists, and offer to delete if so
rc_branch="rc-$RELEASE"
if git rev-parse "$rc_branch" >/dev/null 2>&1; then
  printf "Delete the ${BLUE}rc-${RELEASE}${NC} branch?\n"
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
fi

printf "Opening release ${BLUE}${RELEASE}${NC} in browser; review / update notes and then publish as latest ...\n"
gh release view $RELEASE --web


printf "  Then publish docs: \n"
printf "    ${BLUE}./tools/release/publish-docs.sh ${RELEASE} ${PATH_TO_REPO}${NC}\n"

printf "  Then update example templates to point to it:\n"
if [ -d ~/code/psoxy-example-aws/ ]; then
  AWS_EXAMPLE_DIR="~/code/psoxy-example-aws"
else
  AWS_EXAMPLE_DIR="~/psoxy-example-aws"
fi

if [ -d ~/code/psoxy-example-gcp/ ]; then
  GCP_EXAMPLE_DIR="~/code/psoxy-example-gcp"
else
  GCP_EXAMPLE_DIR="~/psoxy-example-gcp"
fi

printf "    ${BLUE}./tools/release/example-create-release-pr.sh . aws ${AWS_EXAMPLE_DIR}${NC}\n"
printf "    ${BLUE}./tools/release/example-create-release-pr.sh . gcp ${GCP_EXAMPLE_DIR}${NC}\n"

printf "Publish release artifacts: \n"
printf "    ${BLUE}./tools/release/publish-aws-release-artifact.sh ${RELEASE}${NC}\n"

printf "\nPublishing Maven artifacts to GitHub Packages ...\n"
printf "  (requires GitHub token with ${BLUE}write:packages${NC} permission in ${BLUE}~/.m2/settings.xml${NC})\n"
if command -v mvn &> /dev/null; then
  cd "${PATH_TO_REPO}java"
  if mvn clean deploy -DskipTests; then
    printf "${GREEN}✓${NC} Maven artifacts published to GitHub Packages\n"
  else
    printf "${RED}✗${NC} Maven deploy failed. You may need to configure authentication in ~/.m2/settings.xml\n"
    printf "  See: https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry\n"
  fi
else
  printf "${RED}Maven not found.${NC} Skipping Maven artifact deployment.\n"
  printf "  To deploy manually, run: ${BLUE}cd ${PATH_TO_REPO}java && mvn clean deploy${NC}\n"
fi

printf "Update stable demo deployment to point to it: \n"
printf "In ${BLUE}psoxy-demos${NC} repo, run:\n"
printf "    ${BLUE}git checkout -b upgrade-aws-stable-to-${RELEASE}${NC}\n"
printf "    ${BLUE}cd developers/psoxy-dev-stable-aws ${NC}\n"
printf "    ${BLUE} ./upgrade-terraform-modules.sh  ${RELEASE}${NC}\n"
printf "    ${BLUE}terraform apply --auto-approve${NC}\n"
printf "    ${BLUE}git commit -m \"upgrade aws stable to ${RELEASE}\"${NC}\n"
printf "    ${BLUE}git push origin upgrade-aws-stable-to-${RELEASE}${NC}\n"

printf "Prep next rc: \n"
printf "    ${BLUE}./tools/release/prep.sh ${RELEASE} rc-NEXT${NC}\n"