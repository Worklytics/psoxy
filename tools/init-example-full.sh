#!/bin/bash

# colors
RED='\e[0;31m'
BLUE='\e[0;34m'
GREEN='\e[0;32m'
NC='\e[0m' # No Color

REPO_CLONE_BASE_DIR=${1:-".terraform/modules/psoxy/"}
TF_CONFIG_ROOT=`pwd`

if [[ ! -d "$REPO_CLONE_BASE_DIR" ]]; then
  printf "Directory ${RED}${TF_CONFIG_ROOT}${NC} does not exist.\n"
  exit 1
fi

echo "Please choose your host platform:"
echo "1) AWS"
echo "2) GCP"

read -p "Enter your choice [1-2]: " choice
case $choice in
  1)
    HOST_PLATFORM="aws"
    ;;
  2)
    HOST_PLATFORM="gcp"
    ;;
  *)
    printf "${RED}Invalid choice! Please re-run initialization script.${NC}\n"
    exit 1
    ;;
esac

UC_HOST=$(echo "$HOST_PLATFORM" | tr '[:lower:]' '[:upper:]')
printf "You have chosen ${GREEN}${UC_HOST}${NC} as your host platform.\n"


TFVARS_FILE="${TF_CONFIG_ROOT}/terraform.tfvars"

if [ ! -f "${TFVARS_FILE}" ]; then
  printf "Initializing ${BLUE}terraform.tfvars${NC} file for your configuration ...\n"


  printf "Please choose where you intend to run ${BLUE}terraform apply${NC}:\n"
  echo "1) locally (this machine)"
  echo "2) Terraform Cloud (or similar remote CD pipeline)"

  read -p "Enter your choice [1-2]: " choice
  case $choice in
    1)
      DEPLOYMENT_ENV="local"
      ;;
    2)
      DEPLOYMENT_ENV="terraform_cloud"
      ;;
    *)
      printf "${RED}Invalid choice! Please re-run initialization script.${NC}\n"
      exit 1
      ;;
  esac
  echo "" # newline


  if [[ -z "$DEPLOYMENT_ENV" ]]; then
    printf "${RED}No deployment environment selected.${NC} Exiting.\n"
    exit 1;
  fi


  if [ -f "${TF_CONFIG_ROOT}/terraform.tfvars.example.hcl" ]; then
    cp "${TF_CONFIG_ROOT}/terraform.tfvars.example.hcl" "${TFVARS_FILE}"
  else
    touch "${TFVARS_FILE}"
  fi

  ${REPO_CLONE_BASE_DIR}tools/init-tfvars.sh "${TFVARS_FILE}" "${REPO_CLONE_BASE_DIR}" "${DEPLOYMENT_ENV}" $HOST_PLATFORM
fi



# START CREATION OF BUILD SCRIPT
# create example build script, to support building deployment bundle (JAR) outside of Terraform
# (useful for debugging)

BUILD_DEPLOYMENT_BUNDLE_SCRIPT=${TF_CONFIG_ROOT}/build
if [ -f $BUILD_DEPLOYMENT_BUNDLE_SCRIPT ]; then
  rm "$BUILD_DEPLOYMENT_BUNDLE_SCRIPT"
fi

touch "$BUILD_DEPLOYMENT_BUNDLE_SCRIPT"
echo "#!/bin/bash" >> $BUILD_DEPLOYMENT_BUNDLE_SCRIPT

echo "\"${REPO_CLONE_BASE_DIR}tools/build.sh\" $HOST_PLATFORM \"${REPO_CLONE_BASE_DIR}java\"" >> $BUILD_DEPLOYMENT_BUNDLE_SCRIPT
chmod +x "$BUILD_DEPLOYMENT_BUNDLE_SCRIPT"
# END BUILD SCRIPT

# tf module upgrade script

UPGRADE_TF_MODULE_SCRIPT=${TF_CONFIG_ROOT}/upgrade-terraform-modules
if [ -f $UPGRADE_TF_MODULE_SCRIPT ]; then
  rm "$UPGRADE_TF_MODULE_SCRIPT"
fi

touch "$UPGRADE_TF_MODULE_SCRIPT"
echo "#!/bin/bash" >> $UPGRADE_TF_MODULE_SCRIPT
echo "\"${REPO_CLONE_BASE_DIR}tools/upgrade-terraform-modules.sh\" \$1" >> $UPGRADE_TF_MODULE_SCRIPT
chmod +x "$UPGRADE_TF_MODULE_SCRIPT"


# Install test tool (if user agrees)
read -p "Do you want to install the NodeJS-based tooling to test your psoxy instance from this machine? (requires NodeJS/npm) (Y/n) " -n 1 -r
REPLY=${REPLY:-Y}
echo    # Move to a new line
case "$REPLY" in
  [yY][eE][sS]|[yY])
    ${REPO_CLONE_BASE_DIR}tools/install-test-tool.sh "${REPO_CLONE_BASE_DIR}tools"
    ;;
  *)
    echo "Installation of test tool skipped."
    ;;
esac
echo "" # newline

# warn customer to configure their backend
if [ -f "${TF_CONFIG_ROOT}/backend.tf" ]; then
  printf "Your Terraform backend is configured in ${BLUE}backend.tf${NC}. We recommend you review that file and customize it to your needs. "
  printf "By default, your configuration will use a 'local' backend, which is not recommended for production-use.\n"

  if [ "$HOST_PLATFORM" == "aws" ]; then
    printf "As you're hosting proxy in AWS, consider the ${BLUE}s3${NC} backend: https://developer.hashicorp.com/terraform/language/settings/backends/s3\n"
  elif [ "$HOST_PLATFORM" == "gcp" ]; then
    printf "As you're hosting proxy in GCP, consider the ${BLUE}gcs${NC} backend: https://developer.hashicorp.com/terraform/language/settings/backends/gcs\n"
  fi

  printf "Alternatively, you could replace the 'backend' block with a 'cloud' block, and use Terraform Cloud / Enterprise. See https://developer.hashicorp.com/terraform/language/settings/terraform-cloud\n"
fi

printf "\n${GREEN}Initialization complete.${NC}"
printf "If you wish to remove files created by this initalization, run ${BLUE}${REPO_CLONE_BASE_DIR}tools/reset-example.sh${NC}.\n"



