#!/bin/bash

# Arguments: repository name and tag name
RELEASE="$1"
PATH_TO_REPO="${2:-.}"

# Normalize PATH_TO_REPO - ensure it ends with a slash for path concatenation
if [ -n "$PATH_TO_REPO" ] && [ "${PATH_TO_REPO: -1}" != "/" ]; then
  PATH_TO_REPO="${PATH_TO_REPO}/"
fi

COLORSCHEME_SH="$(dirname "$0")/../set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

if [ ! -f "${PATH_TO_REPO}java/pom.xml" ]; then
  printf "${ERR}${PATH_TO_REPO}java/pom.xml not found. set <path-to-repo> argument to point to the root of a psoxy checkout. Exiting.${NC}\n"
  exit 1
fi

CURRENT_RELEASE_NUMBER=$(sed -n 's|[[:space:]]*<revision>\(.*\)</revision>|\1|p' "${PATH_TO_REPO}java/pom.xml" )

if [ -z "$RELEASE" ]; then
  RELEASE="v${CURRENT_RELEASE_NUMBER}"
else
  # maintain compatibility with current interface
  if [ "$RELEASE" != "v${CURRENT_RELEASE_NUMBER}" ]; then
    printf "${ERR}Release version provided, ${RELEASE}, does not match current release version in pom.xml, ${CURRENT_RELEASE_NUMBER}. Exiting.${NC}\n"
    exit 1
  fi
fi

# die on error
set -e

git fetch origin

if git rev-parse "$RELEASE" >/dev/null 2>&1; then
  printf "Tag ${SUCCESS}$RELEASE${NC} already exists.\n"
else
  git checkout main

  git pull origin main

  # verify on main branch and clean status
  CURRENT_BRANCH=$(git branch --show-current)
  if [ "$CURRENT_BRANCH" != "main" ]; then
    printf "${ERR}Current branch is not main. Please checkout main branch and try again.${NC}\n"
    exit 1
  fi
  printf "Tagging ${SUCCESS}$RELEASE${NC} ...\n"
  git tag $RELEASE
fi

printf "Pushing tag ${SUCCESS}$RELEASE${NC} to origin ...\n"
git push origin $RELEASE

if gh release view $RELEASE >/dev/null 2>&1
then
  printf "Release ${SUCCESS}$RELEASE${NC} already exists.\n"
else
  printf "Creating release ${SUCCESS}$RELEASE${NC} in GitHub ...\n"
  gh release create --draft --generate-notes $RELEASE
fi

printf "Updating release notes for ${SUCCESS}$RELEASE${NC} ...\n"
# Fetch release notes
release_notes=$(gh release view $RELEASE --json body -q '.body')

# Remove GitHub username mentions
modified_notes=$(echo -e "$release_notes" | sed -r 's/by @[a-zA-Z0-9_-]+ in//g')

# Update release notes
gh release edit $RELEASE -n "$modified_notes"

# check if rc branch exists, and offer to delete if so
rc_branch="rc-$RELEASE"
if git rev-parse "$rc_branch" >/dev/null 2>&1; then
  printf "Delete the ${INFO}rc-${RELEASE}${NC} branch?\n"
  read -p "(Y/n) " -n 1 -r
  REPLY=${REPLY:-Y}
  echo    # Move to a new line
  case "$REPLY" in
    [yY][eE][sS]|[yY])
       git branch -d "rc-$RELEASE"
      ;;
    *)
      printf "Skipped deletion of ${INFO}rc-$RELEASE${NC}\n"
      ;;
  esac
fi

printf "Opening release ${INFO}${RELEASE}${NC} in browser; review / update notes and then publish as latest ...\n"
gh release view $RELEASE --web

# prompt user to publish mvn artifacts
printf "Publish Maven artifacts to GitHub Packages?\n"
read -p "(Y/n) " -n 1 -r
REPLY=${REPLY:-Y}
echo    # Move to a new line
case "$REPLY" in
  [yY][eE][sS]|[yY])
    LOG_FILE="/tmp/release_${RELEASE}_mvn-artifacts.log"
    set +e  # Temporarily disable exit on error to check exit code
    ./tools/release/publish-mvn-artifacts.sh ${PATH_TO_REPO} &> "${LOG_FILE}"
    EXIT_CODE=$?
    set -e  # Re-enable exit on error
    if [ $EXIT_CODE -ne 0 ]; then
      printf "${ERR}Failed to publish Maven artifacts to GitHub Packages.${NC}\n"
      printf "Please review the error logs: ${INFO}cat ${LOG_FILE}${NC}\n"
      exit $EXIT_CODE
    else
      printf "${SUCCESS}✓${NC} Maven artifacts published to GitHub Packages\n"
      printf "See logs: ${INFO}cat ${LOG_FILE}${NC}\n"
    fi
  ;;
  *)
    printf "Skipped publishing Maven artifacts to GitHub Packages\n"
    printf "To do so manually, run:\n"
    printf "    ${INFO}./tools/release/publish-mvn-artifacts.sh ${PATH_TO_REPO}${NC}\n"
    printf "    or run the GitHub Actions workflow manually: ${INFO}gh workflow run publish-mvn-artifacts.yaml --ref ${RELEASE}${NC}\n"
    ;;
esac

# publish bundles
printf "Publish bundles (via GitHub Actions)?\n"
read -p "(Y/n) " -n 1 -r
REPLY=${REPLY:-Y}
echo    # Move to a new line
case "$REPLY" in
  [yY][eE][sS]|[yY])
    ./tools/release/publish-rc-bundles-via-gh.sh ${RELEASE}
  ;;
  *)
    printf "Skipped publishing bundles (via GitHub Actions)\n"
    printf "To do so manually, run:\n"
    printf "    ${INFO}./tools/release/publish-rc-bundles-via-gh.sh ${RELEASE}${NC}\n"
    ;;
esac

# publish docs
printf "Publish docs?\n"
read -p "(Y/n) " -n 1 -r
REPLY=${REPLY:-Y}
echo    # Move to a new line
case "$REPLY" in
  [yY][eE][sS]|[yY])
    set +e  # Temporarily disable exit on error to check exit code
    ./tools/release/publish-docs.sh ${RELEASE} ${PATH_TO_REPO}
    EXIT_CODE=$?
    set -e  # Re-enable exit on error
    if [ $EXIT_CODE -ne 0 ]; then
      printf "${ERR}Failed to publish docs.${NC}\n"
    else
      printf "${SUCCESS}✓${NC} Docs published\n"
    fi
  ;;
  *)
    printf "Skipped publishing docs\n"
    printf "To do so manually, run:\n"
    printf "    ${INFO}./tools/release/publish-docs.sh ${RELEASE} ${PATH_TO_REPO}${NC}\n"
    ;;
esac

# prep next rc ?
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
    printf "    ${INFO}./tools/release/prep.sh ${RELEASE} rc-NEXT${NC}\n"
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

printf "    ${INFO}./tools/release/example-create-release-pr.sh . aws ${AWS_EXAMPLE_DIR}${NC}\n"
printf "    ${INFO}./tools/release/example-create-release-pr.sh . gcp ${GCP_EXAMPLE_DIR}${NC}\n"

printf "2. Update stable demo deployment to point to it. In ${INFO}psoxy-demos${NC} repo, run:\n"
printf "    ${INFO}./update-stable.sh${NC}\n"

