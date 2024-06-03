#!/bin/bash

# merges release PR to main branch for example repo and creates a release for the example

# colors
RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

SCRIPT_NAME=$0
EXAMPLE_TEMPLATE_REPO=$1
PR_NUMBER=$2
# value of 3 or default to current directory
PATH_TO_REPO=${3:-"./"}


REMOTE_MAIN_REPO_NAME="Worklytics/psoxy"


display_usage() {
  printf "Merges release PR to main branch for example repo and creates a release, with name sync'd to that of main repo\n"
  printf "\n"
  printf "Usage:\n"
  printf "  %s <path-to-example-template-repo> <PR-number>\n" $SCRIPT_NAME
}


# find where user is running this script from, so can return there at end
WORKING_DIRECTORY=`pwd`

if [ "$#" -ne 2 ]; then
  printf "${RED}Unexpected number of parameters.${NC}\n"
  display_usage
  exit 1
fi

if [ ! -d "$PATH_TO_REPO" ]; then
  printf "Directory provided for PATH_TO_REPO, ${RED}'${PATH_TO_REPO}'${NC}, does not exist.\n"
  display_usage
  exit 1
fi

# append / if needed
if [ ! -d "$EXAMPLE_TEMPLATE_REPO" ]; then
  printf "Directory provided for EXAMPLE_TEMPLATE_REPO, ${RED}'${EXAMPLE_TEMPLATE_REPO}'${NC}, does not exist.\n"
  display_usage
  exit 1
fi

# append / if needed
if [[ "${EXAMPLE_TEMPLATE_REPO: -1}" != "/" ]]; then
    EXAMPLE_TEMPLATE_REPO="$EXAMPLE_TEMPLATE_REPO/"
fi

cd $EXAMPLE_TEMPLATE_REPO

# check if PR exists
if ! gh pr view $PR_NUMBER >/dev/null 2>&1; then
  printf "PR ${BLUE}${PR_NUMBER}${NC} does not exist. Exiting.\n"
  exit 1
fi

# get release number
RELEASE_NUMBER=$(gh pr view $PR_NUMBER --json title | jq -r '.title | match("v[0-9]+\\.[0-9]+\\.[0-9]+").string')

if [ -z "$RELEASE_NUMBER" ]; then
  printf "Could not determine release number from PR title. Exiting.\n"
  exit 1
fi

# check if release already exists
if gh release view $RELEASE_NUMBER >/dev/null 2>&1; then
  printf "Release ${BLUE}${RELEASE_NUMBER}${NC} already exists. Exiting.\n"
  exit 1
fi

# check if git tag for release exists
if git rev-parse "$RELEASE_NUMBER" >/dev/null 2>&1; then
  printf "Git tag ${BLUE}${RELEASE_NUMBER}${NC} already exists. Exiting.\n"
  exit 1
fi

# check status of PR
PR_STATUS=$(gh pr view $PR_NUMBER --json state | jq -r '.state')
if [ "$PR_STATUS" != "OPEN" ]; then
  printf "PR ${BLUE}${PR_NUMBER}${NC} is not open. Continuing with cutting release from ${BLUE}main${NC}, presuming it's already merged.\n"
else
  # check if PR is mergeable
  PR_MERGEABLE=$(gh pr view $PR_NUMBER --json mergeable | jq -r '.mergeable')
  if [ "$PR_MERGEABLE" != "MERGEABLE" ]; then
    printf "${RED}PR ${PR_NUMBER} is not mergeable. Exiting.${NC}\n"
    exit 1
  fi

  # confirm all status checks succeeded
  PR_CHECKS_PASSED=$(gh pr view $PR_NUMBER --json statusCheckRollup | jq 'all(.statusCheckRollup[]; .conclusion == "SUCCESS")')
  if [ "$PR_CHECKS_PASSED" != "true" ]; then
    printf "${RED}PR ${PR_NUMBER} does not have all status checks passing. Exiting.${NC}\n"
    printf "Here are the status checks that failed:\n"
    gh pr view $PR_NUMBER --json statusCheckRollup | jq '.statusCheckRollup[] | select(.conclusion == "FAILURE")'
    exit 1
  fi

  # merge the PR to main
  printf "Merging PR ${BLUE}${PR_NUMBER}${NC} to main ...\n"
  gh pr merge $PR_NUMBER --delete-branch --squash
fi


# ensure `main` up-to-date with origin
printf "Ensuring local ${BLUE}main${NC} branch is up-to-date with origin ...\n"
if git fetch origin main --dry-run | grep -q 'up to date'; then
      echo "The local main branch is up to date with origin/main."
  else
    # Check if it can be fast-forwarded
    if git merge-base --is-ancestor HEAD origin/main; then
      echo "The local main branch can be fast-forwarded. Performing 'git pull origin main'..."
      git pull origin main
    else
      printf "${RED}The local copy of main branch cannot be fast-forwarded. Please resolve any conflicts manually.${NC}"
      exit 1
    fi
fi

printf "Tagging ${BLUE}${RELEASE_NUMBER}${NC} ...\n"
git tag $RELEASE_NUMBER
git push origin $RELEASE_NUMBER

printf "Creating release ${BLUE}${RELEASE_NUMBER}${NC} in GitHub ...\n"
PSOXY_RELEASE_URL=$(gh release view ${RELEASE_NUMBER} --repo ${REMOTE_MAIN_REPO_NAME} --json url | jq -r ".url")
touch /tmp/release-notes.md
printf "Update example to psoxy release ${RELEASE_NUMBER}\nSee: ${PSOXY_RELEASE_URL}" >> /tmp/release-notes.md
gh release create $RELEASE_NUMBER --title $RELEASE_NUMBER --notes-file /tmp/release-notes.md
rm /tmp/release-notes.md




