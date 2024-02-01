#!/bin/bash

# Creates a PR to update an example repo to reference the latest release of the main Psoxy repo
#
# Usage:
# ./tools/release/example-create-release-pr.sh ~/psoxy/ aws-all ~/psoxy-example-aws
# ./tools/release/example-create-release-pr.sh ~/psoxy/ gcp ~/psoxy-example-gcp

# colors
RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

SCRIPT_NAME=$0
PATH_TO_REPO=$1
EXAMPLE=$2
EXAMPLE_TEMPLATE_REPO=$3

# find where user is running this script from, so can return there at end
WORKING_DIRECTORY=`pwd`

display_usage() {
    printf "Creates a PR to update an example repo to reference the latest release of the main Psoxy repo\n"
    printf "\n"
    printf "Usage:\n"
    printf "  %s <path-to-repo> <example> <path-to-example-repo>\n" $SCRIPT_NAME
    printf "  %s ~/psoxy/ aws-all ~/psoxy-example-aws\n" $SCRIPT_NAME
    printf "  %s ~/psoxy/ gcp ~/psoxy-example-gcp\n" $SCRIPT_NAME
}

if [ "$#" -ne 3 ]; then
  printf "${RED}Unexpected number of parameters.${NC}\n"
  display_usage
  exit 1
fi
if [ ! -d "$PATH_TO_REPO" ]; then
  printf "Directory provided for PATH_TO_REPO, ${RED}'${PATH_TO_REPO}'${NC}, does not exist.\n"
  display_usage
  exit 1
fi

printf "Creating PR to update example ${BLUE}${EXAMPLE}${NC} in ${BLUE}${EXAMPLE_TEMPLATE_REPO}${NC} to reference latest release of ${BLUE}${PATH_TO_REPO}${NC} ...\n"

# append / if needed
if [[ "${PATH_TO_REPO: -1}" != "/" ]]; then
    PATH_TO_REPO="$PATH_TO_REPO/"
fi

if [ ! -f "${PATH_TO_REPO}java/pom.xml" ]; then
  printf "${RED}${PATH_TO_REPO}java/pom.xml not found. set <path-to-repo> argument to point to the root of a psoxy checkout. Exiting.${NC}\n"
  exit 1
fi

cd $PATH_TO_REPO

# check if any open prs in github
if gh pr list --state open; then
  REPO_NAME=$(gh repo view --json nameWithOwner -q .nameWithOwner)
  printf "${RED}There are open PRs in the ${BLUE}${REPO_NAME}${NC}. Please close them before continuing.${NC}\n"
  gh pr list --web
  cd -
  exit 1
fi

# ensure on `main`
CURRENT_SOURCE_BRANCH=$(git branch --show-current)
if [ "$CURRENT_SOURCE_BRANCH" != "main" ]; then
  printf "Current branch for ${BLUE}$PATH_TO_REPO${NC} is not ${BLUE}main${NC}. Do you want to switch to main? "
  read -p "(Y/n) " -n 1 -r
  REPLY=${REPLY:-Y}
  echo    # Move to a new line
  case "$REPLY" in
    [yY][eE][sS]|[yY])
      git checkout main
      ;;
    *)
      printf "Did not switch to main. Example will be published from ${BLUE}${CURRENT_BRANCH}${NC}.\n"
      ;;
  esac
fi

CURRENT_RELEASE_NUMBER=$(sed -n 's|[[:space:]]*<revision>\(.*\)</revision>|\1|p' "${PATH_TO_REPO}java/pom.xml" )

RELEASE_TAG="v${CURRENT_RELEASE_NUMBER}"

dev_example_path="${PATH_TO_REPO}infra/examples-dev/${EXAMPLE}"

if [ ! -d "$dev_example_path" ]; then
  printf "Directory provided for EXAMPLE, ${RED}'${EXAMPLE}'${NC} not found at ${dev_example_path}, where it's expected to be.\n"
  display_usage
  cd -
  exit 1
fi

if [ ! -d "$EXAMPLE_TEMPLATE_REPO" ]; then
  printf "Directory provided for EXAMPLE_TEMPLATE_REPO, ${RED}'${EXAMPLE_TEMPLATE_REPO}'${NC}, does not exist.\n"
  display_usage
  cd -
  exit 1
fi

# append / if needed
if [[ "${EXAMPLE_TEMPLATE_REPO: -1}" != "/" ]]; then
    EXAMPLE_TEMPLATE_REPO="$EXAMPLE_TEMPLATE_REPO/"
fi

FILES_TO_COPY=("main.tf" "variables.tf" "backend.tf" "google-workspace.tf" "google-workspace-variables.tf" "msft-365.tf" "msft-365-variables.tf" "misc-data-source-variables.tf")

cd "$EXAMPLE_TEMPLATE_REPO"
CURRENT_BRANCH=$(git branch --show-current)
if [ "$CURRENT_BRANCH" != "main" ]; then
  printf "${RED}Current branch in checkout of $EXAMPLE_TEMPLATE_REPO is not main. Please checkout main branch and try again.${NC}\n"
  printf "try ${BLUE}(cd $EXAMPLE_TEMPLATE_REPO && git checkout main && cd $WORKING_DIRECTORY)${NC}\n"
  exit 1
fi

BRANCH_STATUS=$(git status --porcelain)
if [ -n "$BRANCH_STATUS" ]; then
  printf "${RED}Current status of 'main' branch is not clean. Please commit or stash your changes and try again.${NC}\n"

  git status

  printf "Do you want to ${BLUE}git reset --hard${NC}?"
  read -p "(y/N) " -n 1 -r
  REPLY=${REPLY:-N}
  echo    # Move to a new line
  case "$REPLY" in
    [yY][eE][sS]|[yY])
      git reset --hard
      ;;
    *)
      echo "Aborted."
      exit 1
      ;;
  esac
  exit 1
fi

# ensure `main` up-to-date with origin
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

set -e

cd -
for file in "${FILES_TO_COPY[@]}"
do
  if [ -f ${dev_example_path}/${file} ]; then
     echo "copying ${dev_example_path}/${file} to ${EXAMPLE_TEMPLATE_REPO}${file}"
     cp -f ${dev_example_path}/${file} ${EXAMPLE_TEMPLATE_REPO}${file}

     # uncomment Terraform module remotes
     sed -i .bck 's/^\(.*\)# source = "git::\(.*\)"/\1source = "git::\2"/' "${EXAMPLE_TEMPLATE_REPO}${file}"

     # remove references to local modules
     sed -i .bck '/source = "..\/..\/modules\/[^"]*"/d' "${EXAMPLE_TEMPLATE_REPO}${file}"
  fi
done

rm ${EXAMPLE_TEMPLATE_REPO}/*.bck

cp -f ${PATH_TO_REPO}tools/init-example.sh ${EXAMPLE_TEMPLATE_REPO}init
cp -f ${PATH_TO_REPO}tools/check-prereqs.sh ${EXAMPLE_TEMPLATE_REPO}check-prereqs
chmod +x ${EXAMPLE_TEMPLATE_REPO}init
chmod +x ${EXAMPLE_TEMPLATE_REPO}check-prereqs

cd "$EXAMPLE_TEMPLATE_REPO"
git checkout -b "rc-${RELEASE_TAG}"

if [ $? -ne 0 ]; then
  printf "${RED}Failed to create branch rc-${RELEASE_TAG}. does it already exist?${NC}\n"
  exit 1
fi

git commit -a -m "Update example to ${RELEASE_TAG}"
git push origin

if command -v gh &> /dev/null; then
  PR_URL=$(gh pr create --title "update to ${RELEASE_TAG}" --body "update example to ${RELEASE_TAG}" --assignee "@me")
  printf "created PR ${BLUE}${PR_URL}${NC}\n"
  PR_NUMBER=$(gh pr view $PR_URL --json number | jq -r ".number")
  gh pr comment $PR_NUMBER --body "when ready, from your Psoxy checkout, run \`./tools/release-example-publish-release-pr.sh $EXAMPLE_TEMPLATE_REPO $PR_NUMBER\`"
  gh pr view $PR_URL --web
fi

# return us to main, so don't have to do this manually before next release
git checkout main

# should be equivalent to : cd $WORKING_DIRECTORY
cd -

