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

if [ -d ${TF_CONFIG_ROOT}/.terraform/modules/psoxy/ ]; then
  # use checkout of repo done by Terraform
  PSOXY_BASE_DIR=${TF_CONFIG_ROOT}/.terraform/modules/psoxy/
else
  # use checkout of repo on your local machine
  cd ../../..
  PSOXY_BASE_DIR="`pwd`/"
  cd ${TF_CONFIG_ROOT}
fi

if [ ! -f terraform.tfvars ]; then
  printf "Initializing ${BLUE}terraform.tfvars${NC} file for your configuration ...\n"

  TFVARS_FILE="${TF_CONFIG_ROOT}/terraform.tfvars"

  cp ${TF_CONFIG_ROOT}/terraform.tfvars.example $TFVARS_FILE

  ${PSOXY_BASE_DIR}tools/init-tfvars.sh $TFVARS_FILE
else
  printf "${RED}Nothing to initialize. File terraform.tfvars already exists.${NC}\n\n"
fi

# Install test tool
${PSOXY_BASE_DIR}tools/install-test-tool.sh ${PSOXY_BASE_DIR}tools
