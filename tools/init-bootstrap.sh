#!/bin/bash

# colors - see http://stackoverflow.com/questions/5947742/how-to-change-the-output-color-of-echo-in-linux
RED='\033[0;31m'
BLUE='\33[0;34m'
NC='\033[0m' # No Color

TF_CONFIG_ROOT=`pwd`

if [ ! -f terraform.tfvars ]; then
  TFVARS_FILE="${TF_CONFIG_ROOT}/terraform.tfvars"

  cp "${TF_CONFIG_ROOT}/terraform.tfvars.example" "${TFVARS_FILE}"

  # give user some feedback
  printf "Init'd example terraform config. Please open ${BLUE}terraform.tfvars${NC} and customize it to your needs.\n"
  printf "Review ${BLUE}variables.tf${NC} for descriptions of each variable.\n\n"
else
  printf "${RED}Nothing to initialize. File terraform.tfvars already exists!${NC}\n\n"
fi
