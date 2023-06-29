#!/bin/bash

# colors
RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

# Usage: ./tools/publish-legacy-example.sh <repo-root> <example> <release-tag>
# ./tools/publish-legacy-example.sh ~/code/psoxy aws-msft-365 v0.4.26

REPO_ROOT=$1
EXAMPLE=$2
RELEASE_TAG=$3

DEV_EXAMPLE_PATH=${REPO_ROOT}/infra/examples-dev/${EXAMPLE}
EXAMPLE_PATH=${REPO_ROOT}/infra/examples/${EXAMPLE}

if [ ! -d $DEV_EXAMPLE_PATH ]; then
  printf "Dev example ${RED}${EXAMPLE}${NC} does not exist.\n"
  exit 1
fi

if [ ! -d $EXAMPLE_PATH ]; then
  printf "Example ${RED}${EXAMPLE}${NC} does not exist.\n"
  exit 1
fi

FILES_TO_COPY=("main.tf" "variables.tf")

for file in "${FILES_TO_COPY[@]}"
do
  cp -f ${DEV_EXAMPLE_PATH}/${file} ${EXAMPLE_PATH}/${file}
  # uncomment Terraform module remotes
  sed -i .bck 's/^\(.*\)# source = "git::\(.*\)"/\1source = "git::\2"/' "${EXAMPLE_PATH}/${file}"

  sed -i .bck 's|^.*source = "../../mod.*"|#&|' "${EXAMPLE_PATH}/${file}"

  rm -f ${EXAMPLE_PATH}/${file}.bck
done