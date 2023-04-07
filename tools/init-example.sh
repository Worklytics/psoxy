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

TF_INIT_EXIT_CODE=$?
if [ $TF_INIT_EXIT_CODE -ne 0 ]; then
  printf "${RED}Terraform init failed. See above for details. Cannot continue to initialize example configuration.${NC}\n"
  exit 1
fi

if [ -d ${TF_CONFIG_ROOT}/.terraform/modules/psoxy/ ]; then
  # use checkout of repo done by Terraform
  PSOXY_BASE_DIR=${TF_CONFIG_ROOT}/.terraform/modules/psoxy/
else
  # use checkout of repo on your local machine
  cd ../../..
  PSOXY_BASE_DIR="`pwd`/"
  cd ${TF_CONFIG_ROOT}
fi

TFVARS_FILE="${TF_CONFIG_ROOT}/terraform.tfvars"

if [ ! -f $TFVARS_FILE ]; then
  printf "Initializing ${BLUE}terraform.tfvars${NC} file for your configuration ...\n"

  if [ -f ${TF_CONFIG_ROOT}/terraform.tfvars.example.hcl ]; then
    cp ${TF_CONFIG_ROOT}/terraform.tfvars.example.hcl $TFVARS_FILE
  else
    touch $TFVARS_FILE
  fi

  ${PSOXY_BASE_DIR}tools/init-tfvars.sh $TFVARS_FILE $PSOXY_BASE_DIR
else
  printf "${RED}Nothing to initialize. File terraform.tfvars already exists.${NC}\n\n"
fi

# create example build script, to support building deployment bundle (JAR) outside of Terraform
# (useful for debugging)

BUILD_DEPLOYMENT_BUNDLE_SCRIPT=${TF_CONFIG_ROOT}/build
if [ -f $BUILD_DEPLOYMENT_BUNDLE_SCRIPT ]; then
  rm "$BUILD_DEPLOYMENT_BUNDLE_SCRIPT"
fi

touch "$BUILD_DEPLOYMENT_BUNDLE_SCRIPT"
echo "#!/bin/bash" >> $BUILD_DEPLOYMENT_BUNDLE_SCRIPT

# pattern used to grep for provider at top-level of Terraform configuration
TOP_LEVEL_PROVIDER_PATTERN="^├── provider\[registry.terraform.io/hashicorp"
AWS_PROVIDER_COUNT=$(terraform providers | grep "${TOP_LEVEL_PROVIDER_PATTERN}/aws" | wc -l)
AWS_HOSTED=$(test $AWS_PROVIDER_COUNT -ne 0)
if [ $AWS_HOSTED ]; then
  HOST_PLATFORM="aws"
else
  HOST_PLATFORM="gcp"
fi
echo "${PSOXY_BASE_DIR}tools/build.sh $HOST_PLATFORM ${PSOXY_BASE_DIR}java" >> $BUILD_DEPLOYMENT_BUNDLE_SCRIPT
chmod +x "$BUILD_DEPLOYMENT_BUNDLE_SCRIPT"


# Install test tool
${PSOXY_BASE_DIR}tools/install-test-tool.sh ${PSOXY_BASE_DIR}tools
