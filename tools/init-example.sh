#!/bin/bash

# Psoxy init script
#
# this is meant to be run from within a Terraform configuration for Psoxy, modeled on one of our
# examples
# see: https://github.com/Worklytics/psoxy/tree/main/infra/examples

# colors
RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

TF_CONFIG_ROOT=`pwd`

if ! terraform -v &> /dev/null ; then
  printf "${RED}Terraform not available; required for this Psoxy example. See https://github.com/Worklytics/psoxy#prerequisites ${NC}\n"
  exit 1
fi

# initialize terraform, which downloads dependencies into `.terraform` directory
printf "Initializing ${BLUE}psoxy${NC} Terraform configuration ...\n"
terraform init

PSOXY_BASE_DIR=${TF_CONFIG_ROOT}/.terraform/modules/psoxy/

printf "Initializing ${BLUE}terraform.tfvars${NC} file for your configuration ...\n"
if [ ! -f terraform.tfvars ]; then
  TFVARS_FILE="${TF_CONFIG_ROOT}/terraform.tfvars"

  cp ${TF_CONFIG_ROOT}/terraform.tfvars.example $TFVARS_FILE

  # append root of checkout automatically
  echo "# this points to the directory where Java source to be compiled into deployment JAR is located" >> $TFVARS_FILE
  echo "# by default, it points to .terraform, where terraform clones the main psxoy repo" >> $TFVARS_FILE
  echo "# if you have a local clone of the psoxy repo you prefer to use, change this to point there" >> $TFVARS_FILE
  echo "psoxy_base_dir                = \"${PSOXY_BASE_DIR}\"" >> $TFVARS_FILE

  # give user some feedback
  printf "Init'd example terraform config. Please open ${BLUE}terraform.tfvars${NC} and customize it to your needs.\n"
  printf "Review ${BLUE}variables.tf${NC} for descriptions of each variable.\n\n"
else
  printf "${RED}Nothing to initialize. File terraform.tfvars already exists.${NC}\n\n"
fi

TEST_TOOL_ROOT=${PSOXY_BASE_DIR}/tools/psoxy-test

if [ ! -d ${TEST_TOOL_ROOT} ]; then
  printf "${RED}No test tool source found at ${TEST_TOOL_ROOT}. Failed to install test tool.${NC}\n"
  exit
fi

if npm -v &> /dev/null ; then
  printf "Installing ${BLUE}psoxy-test${NC} tool ...\n"
  cd ${PSOXY_BASE_DIR}/tools/psoxy-test
  npm --no-audit --no-fund --prefix ${PSOXY_BASE_DIR}/tools/psoxy-test install
else
  printf "${RED}NPM / Node.JS not available; could not install test tool. We recommend installing Node.JS ( https://nodejs.org/ ), then re-running this init script.${NC}\n"
fi

