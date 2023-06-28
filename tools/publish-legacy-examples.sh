#!/bin/bash

# colors
RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

# Usage: ./tools/publish-legacy-examples.sh <repo-root> <release-tag>
# ./tools/publish-legacy-examples.sh ~/code/psoxy v0.4.26

REPO_ROOT=$1
RELEASE_TAG=$2

EXAMPLES=("aws-msft-365" "aws-google-workspace" "gcp-google-workspace")

for EXAMPLE in "${EXAMPLES[@]}"; do
    ${1}/tools/publish-legacy-example.sh ${REPO_ROOT} ${EXAMPLE} ${RELEASE_TAG}
done