#!/bin/bash

# colors
RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

# Usage:
# ./release-examples.sh <path-to-example> <path-to-aws-repo>
# ./release-example.sh v0.4.25 ~/code/psoxy/infra/examples-dev/aws-all ~/psoxy-example-aws
# ./release-example.sh v0.4.25 ~/code/psoxy/infra/examples-dev/gcp ~/psoxy-example-gcp

RELEASE_TAG=$1
DEV_EXAMPLE_PATH=$2
EXAMPLE_TEMPLATE_REPO=$3

if [ -z "$RELEASE_TAG" ]; then
  printf "${RED}No arguments passed.${NC}\n"
  printf "Usage:\n"
  printf "  ./release-example.sh <release-tag> <path-to-example> <path-to-aws-repo>\n"
  printf "  ./release-example.sh v0.4.25 ~/code/psoxy/infra/examples-dev/aws-all ~/psoxy-example-aws\n"
  printf "  ./release-example.sh v0.4.25 ~/code/psoxy/infra/examples-dev/gcp ~/psoxy-example-gcp\n"
  exit 1
fi

FILES_TO_COPY=("main.tf" "variables.tf" "google-workspace.tf" "google-workspace-variables.tf" "msft-365.tf" "msft-365-variables.tf")

cd "$EXAMPLE_TEMPLATE_REPO"
CURRENT_BRANCH=$(git branch --show-current)
if [ "$CURRENT_BRANCH" != "main" ]; then
  printf "${RED}Current branch is not main. Please checkout main branch and try again.${NC}\n"
  exit 1
fi

BRANCH_STATUS=$(git status --porcelain)
if [ -n "$BRANCH_STATUS" ]; then
  printf "${RED}Current status of 'main' branch is not clean. Please commit or stash your changes and try again.${NC}\n"
  exit 1
fi

git checkout -b "rc-${RELEASE_TAG}"

if [ $? -ne 0 ]; then
  printf "${RED}Failed to create branch rc-${RELEASE_TAG}. does it already exist?${NC}\n"
  exit 1
fi

set -e

cd -
for file in "${FILES_TO_COPY[@]}"
do
  cp -f ${DEV_EXAMPLE_PATH}/${file} ${EXAMPLE_TEMPLATE_REPO}/${file}

  # uncomment Terraform module remotes
  sed -i .bck 's/^\(.*\)# source = "git::\(.*\)"/\1source = "git::\2"/' "${EXAMPLE_TEMPLATE_REPO}/${file}"

  # remove references to local modules
  sed -i .bck '/source = "..\/..\/modules\/[^"]*"/d' "${EXAMPLE_TEMPLATE_REPO}/${file}"

done

rm ${EXAMPLE_TEMPLATE_REPO}/*.bck

set -e

cp -f ./init-example.sh ${EXAMPLE_TEMPLATE_REPO}/init
cp -f ./check-prereqs.sh ${EXAMPLE_TEMPLATE_REPO}/check-prereqs
chmod +x ${EXAMPLE_TEMPLATE_REPO}/init
chmod +x ${EXAMPLE_TEMPLATE_REPO}/check-prereqs

cd "$EXAMPLE_TEMPLATE_REPO"
git commit -a -m "Update example to ${RELEASE_TAG}"
git push origin

if command -v gh &> /dev/null; then
  gh pr create --title "update to ${RELEASE_TAG}" --body "update example to ${RELEASE_TAG}"
fi

cd -
