#!/bin/bash

# script to create PR to merge rc- branch to main

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

touch rc_to_main.md
echo "$RELEASE back to main" >> rc_to_main.md
cat tools/release/test_plan.md >> rc_to_main.md
echo "" >> rc_to_main.md
echo "Next steps, after that's merged to \`main\`:" >> rc_to_main.md
echo "  1. publish the release: \`./tools/release/publish.sh $RELEASE\`" >> rc_to_main.md
echo "  2. update stable deployment in demos repo to point to $RELEASE" >> rc_to_main.md
echo "  3. prep next rc: \`./tools/release/prep.sh $RELEASE rc-v{x.y.z}\`" >> rc_to_main.md

PR_URL=$(gh pr create --title "$RELEASE" --body-file rc_to_main.md --base main --assignee "@me")
PR_NUMBER=$(echo $PR_URL | sed -n 's/.*\/pull\/\([0-9]*\).*/\1/p')

rm rc_to_main.md

# this still doesn't seem to work ...
gh pr merge $PR_NUMBER --auto --merge --subject "release $RELEASE from PR #${PR_NUMBER}" --body "release $RELEASE from PR #${PR_NUMBER}"

printf "created PR ${GREEN}${PR_URL}${NC} and set to auto-merge to ${BLUE}main${NC}\n"

printf "Next steps, after that's merged to ${BLUE}main${NC}:\n"
printf "  1. publish the release: ${BLUE}./tools/release/publish.sh $RELEASE${NC}\n"
printf "  2. update stable deployment in demos repo to point to ${BLUE}$RELEASE${NC}\n"
printf "  3. prep next rc : ${BLUE}./tools/release/prep.sh $RELEASE rc-{x.y.z}${NC}\n"


