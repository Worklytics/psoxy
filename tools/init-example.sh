#!/bin/bash

# colors - see http://stackoverflow.com/questions/5947742/how-to-change-the-output-color-of-echo-in-linux
RED='\033[0;31m'
BLUE='\33[0;34m'
NC='\033[0m' # No Color


TF_CONFIG_ROOT=`pwd`

# initialize terraform, which downloads dependencies into `.terraform` directory
terraform init

PSOXY_BASE_DIR=${TF_CONFIG_ROOT}/.terraform/modules/psoxy/

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
  printf "${RED}Nothing to initialize. File terraform.tfvars already exists!${NC}\n\n"
fi

if which node > /dev/null; then
  printf "Node available. Installing ${BLUE}psoxy-test${NC} tool ...\n"
  cd ${PSOXY_BASE_DIR}/tools/psoxy-test
  npm --no-audit --no-fund --prefix ${PSOXY_BASE_DIR}/tools/psoxy-test install
else
  printf "${RED}Node.JS not available; could not install test tool.${NC}\n"
fi

