#!/bin/bash

# colors
RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

# Usage: ./tools/release/publish-legacy-examples.sh <repo-root> <release-tag>
# ./tools/release/publish-legacy-examples.sh ~/code/psoxy v0.4.26

REPO_ROOT=$1
RELEASE_TAG=$2

if [ -z "$REPO_ROOT" ]; then
  printf "${RED}Repo root not provided${NC}\n"
  printf "Usage: ./tools/release/publish-legacy-examples.sh <repo-root> <release-tag>\n"
  exit 1
fi

EXAMPLES=("aws-msft-365" "aws-google-workspace" "gcp-google-workspace")

for EXAMPLE in "${EXAMPLES[@]}"; do
    ${1}/tools/release/lib/publish-legacy-example.sh ${REPO_ROOT} ${EXAMPLE} ${RELEASE_TAG}
done
