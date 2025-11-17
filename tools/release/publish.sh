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

# prompt user to publish mvn artifacts
printf "Publish Maven artifacts to GitHub Packages?\n"
read -p "(Y/n) " -n 1 -r
REPLY=${REPLY:-Y}
echo    # Move to a new line
case "$REPLY" in
  [yY][eE][sS]|[yY])
    LOG_FILE="/tmp/release_${RELEASE}_mvn-artifacts.log"
    ./tools/release/publish-mvn-artifacts.sh ${PATH_TO_REPO} &> "${LOG_FILE}"
    if [ $? -ne 0 ]; then
      printf "${RED}Failed to publish Maven artifacts to GitHub Packages. Exiting.${NC}\n"
      printf "Please review the error logs: ${BLUE}cat ${LOG_FILE}${NC}\n"
    else
      printf "${GREEN}✓${NC} Maven artifacts published to GitHub Packages\n"
      printf "See logs: ${BLUE}cat ${LOG_FILE}${NC}\n"
    fi
  ;;
  *)
    printf "Skipped publishing Maven artifacts to GitHub Packages\n"
    printf "To do so manually, run:\n"
    printf "    ${BLUE}./tools/release/publish-mvn-artifacts.sh ${PATH_TO_REPO}${NC}\n"
    ;;
esac


# aws bundle, as above
printf "Publish AWS bundle to S3 bucket?\n"
read -p "(Y/n) " -n 1 -r
REPLY=${REPLY:-Y}
echo    # Move to a new line
case "$REPLY" in
  [yY][eE][sS]|[yY])
    LOG_FILE="/tmp/release_${RELEASE}_aws-bundle.log"
    ./tools/release/publish-aws-bundle.sh ${PATH_TO_REPO} &> "${LOG_FILE}"
    if [ $? -ne 0 ]; then
      printf "${RED}Failed to publish AWS bundle to S3 bucket. Exiting.${NC}\n"
      printf "Please review the error logs: ${BLUE}cat ${LOG_FILE}${NC}\n"
    else
      printf "${GREEN}✓${NC} AWS bundle published to S3 bucket\n"
      printf "See logs: ${BLUE}cat ${LOG_FILE}${NC}\n"
    fi
  ;;
  *)
    printf "Skipped publishing AWS bundle to S3 bucket\n"
    printf "To do so manually, run:\n"
    printf "    ${BLUE}./tools/release/publish-aws-bundle.sh ${RELEASE}${NC}\n"
    ;;
esac


# gcp bundle, as above
printf "Publish GCP bundle to GCS bucket?\n"
read -p "(Y/n) " -n 1 -r
REPLY=${REPLY:-Y}
echo    # Move to a new line
case "$REPLY" in
  [yY][eE][sS]|[yY])
    LOG_FILE="/tmp/release_${RELEASE}_gcp-bundle.log"
    ./tools/release/publish-gcp-bundle.sh ${PATH_TO_REPO} &> "${LOG_FILE}"
    if [ $? -ne 0 ]; then
      printf "${RED}Failed to publish GCP bundle to GCS bucket. Exiting.${NC}\n"
      printf "Please review the error logs: ${BLUE}cat ${LOG_FILE}${NC}\n"
    else
      printf "${GREEN}✓${NC} GCP bundle published to GCS bucket\n"
      printf "See logs: ${BLUE}cat ${LOG_FILE}${NC}\n"
    fi
  ;;
  *)
    printf "Skipped publishing GCP bundle to GCS bucket\n"
    printf "To do so manually, run:\n"
    printf "    ${BLUE}./tools/release/publish-gcp-bundle.sh ${RELEASE}${NC}\n"
    ;;
esac

# publish docs
printf "Publish docs?\n"
read -p "(Y/n) " -n 1 -r
REPLY=${REPLY:-Y}
echo    # Move to a new line
case "$REPLY" in
  [yY][eE][sS]|[yY])
    LOG_FILE="/tmp/release_${RELEASE}_docs.log"
    ./tools/release/publish-docs.sh ${RELEASE} ${PATH_TO_REPO} &> "${LOG_FILE}"
    if [ $? -ne 0 ]; then
      printf "${RED}Failed to publish docs. Exiting.${NC}\n"
      printf "Please review the error logs: ${BLUE}cat ${LOG_FILE}${NC}\n"
    else
      printf "${GREEN}✓${NC} Docs published\n"
      printf "See logs: ${BLUE}cat ${LOG_FILE}${NC}\n"
    fi
  ;;
  *)
    printf "Skipped publishing docs\n"
    printf "To do so manually, run:\n"
    printf "    ${BLUE}./tools/release/publish-docs.sh ${RELEASE} ${PATH_TO_REPO}${NC}\n"
    ;;
esac

# prep next ct ?
printf "Prep next rc?\n"
read -p "(Y/n) " -n 1 -r
REPLY=${REPLY:-Y}
echo    # Move to a new line
case "$REPLY" in
  [yY][eE][sS]|[yY])
    ./tools/release/prep.sh ${RELEASE} rc-NEXT
  ;;
  *)
    printf "Skipped prepping next rc\n"
    printf "To do so manually, run:\n"
    printf "    ${BLUE}./tools/release/prep.sh ${RELEASE} rc-NEXT${NC}\n"
    ;;
esac

printf "Next steps: \n"
printf "  1. update example templates to point to it:\n"

# particular to my machine, but just for examples ...
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

printf "2. Update stable demo deployment to point to it. In ${BLUE}psoxy-demos${NC} repo, run:\n"
printf "    ${BLUE}./update-stable.sh${NC}\n"
