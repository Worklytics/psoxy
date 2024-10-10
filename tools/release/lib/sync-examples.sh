#!/bin/bash

# sync from a 'source of truth' example to others
#   this is alternative vs
#      - using symlinks between examples, which Terraform doesn't support
#      - using hardlinks between examples, which become de-coupled after git clones/branching

# Usage ./tools/release/sync-examples.sh <path-to-repo>
#  ./tools/release sync-examples.sh ~/code/psoxy/

RED='\e[0;31m'
GREEN='\e[0;32m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

PATH_TO_REPO=$1


if [ -z "$PATH_TO_REPO" ]; then
  printf "${RED}No arguments passed.${NC}\n"
  printf "Usage:\n"
  printf "  ./sync-examples.sh <path-to-repo>\n"
  printf "  ./sync-examples.sh ~/code/psoxy/\n"
  exit 1
fi

SOURCE_OF_TRUTH="aws"
REPLICAS=("gcp")
FILES_TO_COPY=("google-workspace.tf" "google-workspace-variables.tf" "misc-data-source-variables.tf" "msft-365-variables.tf")

for replica in "${REPLICAS[@]}"
do
  for file in "${FILES_TO_COPY[@]}"
  do
    PATH_TO_FILE=${PATH_TO_REPO}infra/examples-dev/${SOURCE_OF_TRUTH}/${file}
    if [ -f $PATH_TO_FILE ]; then
     cp -f $PATH_TO_FILE ${PATH_TO_REPO}infra/examples-dev/${replica}/${file}
    fi
  done
done
