#!/bin/bash

# Use a local Azure CLI config directory if present to avoid conflicts with other Azure tenants
if [ -d "${PWD}/.azure" ]; then
    export AZURE_CONFIG_DIR="${PWD}/.azure"
fi
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
# Source centralized color scheme
source "$(dirname "$0")/set-term-colorscheme.sh"

EXPLICIT_REPO_CLONE_DIR=$1

TF_CONFIG_ROOT=`pwd`

if ! terraform -v &> /dev/null ; then
  printf "${ERR}Terraform not available; required for this Psoxy example. See https://github.com/Worklytics/psoxy#prerequisites ${NC}\n"
  exit 1
fi

# initialize terraform, which downloads dependencies into `.terraform` directory
printf "Initializing ${CODE}psoxy${NC} Terraform configuration ...\n"
terraform init

TF_INIT_EXIT_CODE=$?
if [[ $TF_INIT_EXIT_CODE -ne 0 ]]; then
  printf "${ERR}Terraform init failed. See above for details. Cannot continue to initialize example configuration.${NC}\n"
  exit 1
fi

# determine where the repo is cloned
if [[ -z "$EXPLICIT_REPO_CLONE_DIR" ]]; then
  if [[ -d ".terraform/modules/psoxy/" ]]; then
    REPO_CLONE_BASE_DIR=".terraform/modules/psoxy/"
  else
    printf "${ERR}No explicit path to repo clone provided, and 'psoxy' module in Terraform configuration.${NC}\n"
    printf "Try running this script with a Terraform module named 'psoxy' that references to GitHub repo https://github.com/Worklytics/psoxy, or pass the path to a clone of that as the first argument to the script.\n"
    printf " eg ${CODE}./init ~/code/psoxy${NC}\n"
    exit 1
  fi
else
  # Walk up from the given path to find the repo root (identified by tools/init-example-full.sh)
  CANDIDATE="$EXPLICIT_REPO_CLONE_DIR"
  # strip trailing slash for consistent dirname handling
  CANDIDATE="${CANDIDATE%/}"
  # normalize to an absolute path so dirname traversal always makes progress toward /
  if [[ -d "$CANDIDATE" ]]; then
    CANDIDATE="$(cd "$CANDIDATE" 2>/dev/null && pwd -P)"
  else
    CANDIDATE_PARENT="$(dirname "$CANDIDATE")"
    CANDIDATE_BASENAME="$(basename "$CANDIDATE")"
    CANDIDATE="$(cd "$CANDIDATE_PARENT" 2>/dev/null && printf "%s/%s" "$(pwd -P)" "$CANDIDATE_BASENAME")"
  fi

  FOUND_REPO_ROOT=""
  while [[ -n "$CANDIDATE" ]] && [[ "$CANDIDATE" != "/" ]]; do
    if [[ -f "${CANDIDATE}/tools/init-example-full.sh" ]]; then
      FOUND_REPO_ROOT="$CANDIDATE"
      break
    fi
    NEXT_CANDIDATE="$(dirname "$CANDIDATE")"
    if [[ "$NEXT_CANDIDATE" == "$CANDIDATE" ]]; then
      break
    fi
    CANDIDATE="$NEXT_CANDIDATE"
  done

  if [[ -z "$FOUND_REPO_ROOT" ]]; then
    printf "${ERR}Could not find repo root (tools/init-example-full.sh) at or above: ${EXPLICIT_REPO_CLONE_DIR}${NC}\n"
    printf "Pass the path to the root of a clone of https://github.com/Worklytics/psoxy as the first argument.\n"
    printf " eg ${CODE}./init ~/code/psoxy${NC}\n"
    exit 1
  fi

  # append trailing slash
  REPO_CLONE_BASE_DIR="${FOUND_REPO_ROOT}/"
fi

# pass control to the full init script.
"${REPO_CLONE_BASE_DIR}/tools/init-example-full.sh" $REPO_CLONE_BASE_DIR
