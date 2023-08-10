#!/bin/bash

# Usage ./tools/release/rc-to-main.sh <release>

RED='\e[0;31m'
GREEN='\e[0;32m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

RELEASE=$1

if [ -z "$RELEASE" ]; then
  printf "${RED}Release version not specified. Exiting.${NC}\n"
  printf "Usage: ${BLUE}./tools/release/rc-to-main.sh <release>${NC}\n"
  exit 1;
fi

# Get the current git branch
current_branch=$(git symbolic-ref --short HEAD)

# Expected branch
expected_branch="rc-$RELEASE"

# Check if the current branch is the expected branch
if [ "$current_branch" != "$expected_branch" ]; then
  # If not, print an error message and exit with a non-zero status
  printf "${RED}Error: Current git branch $current_branch is not the expected release candidate: $expected_branch.${NC}"
  exit 1
fi

printf "Ensuring ${BLUE}${expected_branch}${NC} up to date with all changes from origin ...\n"
git fetch origin
if [[ $(git log "${expected_branch}..origin/${expected_branch}" --oneline) ]]; then
    printf "${RED}Error: ${expected_branch} and origin/${expected_branch} are not in sync!${NC}\n"
    exit 1
fi

PR_URL=$(gh pr create --title "$RELEASE" --body "$RELEASE back to main" --base main --assignee "@me")
PR_NUMBER=$(echo $PR_URL | sed -n 's/.*\/pull\/\([0-9]*\).*/\1/p').

gh pr merge $PR_NUMBER --merge --auto

printf "created PR ${GREEN}${PR_URL}${NC} and set to auto-merge to ${BLUE}main${NC}\n"

printf "Next steps, after that's merged to ${BLUE}main${NC}:\n"
printf "  Publish the release: ${BLUE}./tools/release/publish.sh $RELEASE${NC}\n"
printf "  Update example templates to point to it:\n"
printf "    ${BLUE}./tools/release/examples.sh $RELEASE . aws-all ~/psoxy-example-aws${NC}\n"
printf "    ${BLUE}./tools/release/examples.sh $RELEASE . gcp ~/psoxy-example-gcp${NC}\n"

