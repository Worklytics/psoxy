#!/bin/bash

# colors
RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

# Usage:
# ./tools/release.example.sh v0.4.25 ~/code/psoxy/ aws-all ~/psoxy-example-aws
# ./tools/release/example.sh v0.4.25 ~/code/psoxy/ gcp ~/psoxy-example-gcp

RELEASE_TAG=$1
PATH_TO_REPO=$2
EXAMPLE=$3
EXAMPLE_TEMPLATE_REPO=$4

display_usage() {
    printf "Usage:\n"
    printf "  ./release-example.sh <release-tag> <path-to-repo> <example> <path-to-example-repo>\n"
    printf "  ./release-example.sh v0.4.25 ~/code/psoxy/ aws-all ~/psoxy-example-aws\n"
    printf "  ./release-example.sh v0.4.25 ~/code/psoxy/ gcp ~/psoxy-example-gcp\n"
}

if [ "$#" -ne 4 ]; then
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
if [[ "${PATH_TO_REPO: -1}" != "/" ]]; then
    PATH_TO_REPO="$PATH_TO_REPO/"
fi

if [ ! -f "${PATH_TO_REPO}java/pom.xml" ]; then
  printf "${RED}${PATH_TO_REPO}java/pom.xml not found. set <path-to-repo> argument to point to the root of a psoxy checkout. Exiting.${NC}\n"
  exit 1
fi

dev_example_path="${PATH_TO_REPO}infra/examples-dev/${EXAMPLE}"

if [ ! -d "$dev_example_path" ]; then
  printf "Directory provided for EXAMPLE, ${RED}'${EXAMPLE}'${NC} not found at ${dev_example_path}, where it's expected to be.\n"
  display_usage
  exit 1
fi

if [ ! -d "$EXAMPLE_TEMPLATE_REPO" ]; then
  printf "Directory provided for EXAMPLE_TEMPLATE_REPO, ${RED}'${EXAMPLE_TEMPLATE_REPO}'${NC}, does not exist.\n"
  display_usage
  exit 1
fi

# append / if needed
if [[ "${EXAMPLE_TEMPLATE_REPO: -1}" != "/" ]]; then
    EXAMPLE_TEMPLATE_REPO="$EXAMPLE_TEMPLATE_REPO/"
fi

FILES_TO_COPY=("main.tf" "variables.tf" "google-workspace.tf" "google-workspace-variables.tf" "msft-365.tf" "msft-365-variables.tf" "misc-data-source-variables.tf")

cd "$EXAMPLE_TEMPLATE_REPO"
CURRENT_BRANCH=$(git branch --show-current)
if [ "$CURRENT_BRANCH" != "main" ]; then
  printf "${RED}Current branch in checkout of $EXAMPLE_TEMPLATE_REPO is not main. Please checkout main branch and try again.${NC}\n"
  printf "try ${BLUE}(cd $EXAMPLE_TEMPLATE_REPO && git checkout main)${NC}\n"
  exit 1
fi

BRANCH_STATUS=$(git status --porcelain)
if [ -n "$BRANCH_STATUS" ]; then
  printf "${RED}Current status of 'main' branch is not clean. Please commit or stash your changes and try again.${NC}\n"
  exit 1
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

set -e

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
  gh pr create --title "update to ${RELEASE_TAG}" --body "update example to ${RELEASE_TAG}" --assignee "@me" --web
fi

# return us to main, so don't have to do this manually before next release
git checkout main

cd -

