#!/bin/bash

# colors - see http://stackoverflow.com/questions/5947742/how-to-change-the-output-color-of-echo-in-linux
RED='\033[0;31m'
BLUE='\33[0;34m'
NC='\033[0m' # No Color

TF_CONFIG_ROOT=`pwd`
cd ../../..
PSOXY_BASE_DIR=`pwd`


if [ ! -f terraform.tfvars ]; then
  TFVARS_FILE="${TF_CONFIG_ROOT}/terraform.tfvars"

  cp ${TF_CONFIG_ROOT}/terraform.tfvars.example $TFVARS_FILE

  # append root of checkout automatically
  echo "psoxy_base_dir                = \"${PSOXY_BASE_DIR}/.terraform/modules/psoxy/" >> $TFVARS_FILE

  # give user some feedback
  printf "Init'd example terraform config. Please open ${BLUE}terraform.tfvars${NC} and customize it to your needs.\n"
  printf "Review ${BLUE}variables.tf${NC} for descriptions of each variable.\n\n"
else
  printf "${RED}Nothing to initialize. File terraform.tfvars already exists!${NC}\n\n"
fi

if which node > /dev/null; then
  printf "Node available. Installing ${BLUE}psoxy-test${NC} tool ...\n"
  cd ${PSOXY_BASE_DIR}/tools/psoxy-test
  npm --prefix ${PSOXY_BASE_DIR}/tools/psoxy-test install
else
  printf "${RED}Node.JS not available; could not install test tool.${NC}\n"
fi

