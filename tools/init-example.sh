#!/bin/bash

# Psoxy init script - lite version
#
# Usage:
#   ./tools/init-example.sh
#   ./tools/init-example.sh <repo-root>
#
# if <repo-root> is omitted, presumes it's being run at the root of the Terraform
# configuration
#
# this is meant to be run from within a Terraform configuration for Psoxy, modeled on one of our
# examples. Copied as the `init` script in each example.
#
# this is a 'thin' version, expected to be duplicated across multiple examples and then leverage
# that 'terraform init' will do a clone of the repo, in which a longer init script will be provided
#
#
# Testing:
#   - within example directory, such as `infra/examples-dev/aws`:
#     ../../../tools/init-example.sh ~/code/psoxy
#
#   to repeat:
#     ../../../tools/reset-example.sh

# colors
RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color


EXPLICIT_REPO_CLONE_DIR=$1

TF_CONFIG_ROOT=`pwd`

if ! terraform -v &> /dev/null ; then
  printf "${RED}Terraform not available; required for this Psoxy example. See https://github.com/Worklytics/psoxy#prerequisites ${NC}\n"
  exit 1
fi

# initialize terraform, which downloads dependencies into `.terraform` directory
printf "Initializing ${BLUE}psoxy${NC} Terraform configuration ...\n"
terraform init

TF_INIT_EXIT_CODE=$?
if [[ $TF_INIT_EXIT_CODE -ne 0 ]]; then
  printf "${RED}Terraform init failed. See above for details. Cannot continue to initialize example configuration.${NC}\n"
  exit 1
fi

# determine where the repo is cloned
if [[ -z "$EXPLICIT_REPO_CLONE_DIR" ]]; then
  if [[ -d ".terraform/modules/psoxy/" ]]; then
    REPO_CLONE_BASE_DIR=".terraform/modules/psoxy/"
  else
    printf "${RED}No explicit path to repo clone provided, and 'psoxy' module in Terraform configuration.${NC}\n"
    printf "Try running this script with a Terraform module named 'psoxy' that references to GitHub repo https://github.com/Worklytics/psoxy, or pass the path to a clone of that as the first argument to the script.\n"
    printf " eg ${BLUE}./init ~/code/psoxy${NC}\n"
    exit 1
  fi
else
  # append trailing slash if not present
  if [[ "${EXPLICIT_REPO_CLONE_DIR}" != */ ]]; then
      EXPLICIT_REPO_CLONE_DIR="${EXPLICIT_REPO_CLONE_DIR}/"
  fi

  REPO_CLONE_BASE_DIR="$EXPLICIT_REPO_CLONE_DIR"
fi

# pass control to the full init script.
"${REPO_CLONE_BASE_DIR}/tools/init-example-full.sh" $REPO_CLONE_BASE_DIR
