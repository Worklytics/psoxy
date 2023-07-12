#!/bin/bash

SCRIPT_VERSION="rc-v0.4.30"

# colors
RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

REPO_CLONE_BASE_DIR=${1:-".terraform/modules/psoxy/"}
TF_CONFIG_ROOT=`pwd`

if [[ ! -d "$REPO_CLONE_BASE_DIR" ]]; then
  printf "Directory ${RED}${TF_CONFIG_ROOT}${NC} does not exist.\n"
  exit 1
fi


TFVARS_FILE="${TF_CONFIG_ROOT}/terraform.tfvars"

if [ ! -f "${TFVARS_FILE}" ]; then
  printf "Initializing ${BLUE}terraform.tfvars${NC} file for your configuration ...\n"



  # determine terraform apply location
  read -p "Do you wish to run 'terraform apply' locally on this machine? (Y/n) " -n 1 -r

  REPLY=${REPLY:-Y}
  case "$REPLY" in
    [yY][eE][sS]|[yY])
      echo "Deployment environment set to 'local'."
      DEPLOYMENT_ENV="local"
      ;;
    [nN]|[oO])
      ;;
    *)
      printf "${RED}Invalid input${NC}\n"
      exit 1
      ;;
  esac
  echo "" # newline

  if [[ -z "$DEPLOYMENT_ENV" ]]; then
    read -p "Do you wish to run 'terraform apply' in Terraform Cloud? (Y/n) " -n 1 -r

    REPLY=${REPLY:-Y}
    case "$REPLY" in
      [yY][eE][sS]|[yY])
        echo "Deployment environment set to 'terraform_cloud'."
        DEPLOYMENT_ENV="terraform_cloud"
        ;;
      [nN]|[oO])
        ;;
      *)
        printf "${READ}Invalid input${NC}\n"
        exit 1
        ;;
    esac
    echo "" # newline
  fi


  if [[ -z "$DEPLOYMENT_ENV" ]]; then
    read -p "Do you wish to run 'terraform apply' in GitHub Actions? (Y/n) " -n 1 -r

    REPLY=${REPLY:-Y}
    case "$REPLY" in
      [yY][eE][sS]|[yY])
        echo "Deployment environment set to 'github_action'."
        DEPLOYMENT_ENV="github_action"
        ;;
      [nN]|[oO])
        ;;
      *)
        printf "${RED}Invalid input${NC}\n"
        exit 1
        ;;
    esac
    echo "" # newline
  fi

  if [[ -z "$DEPLOYMENT_ENV" ]]; then
    printf "${RED}No deployment environment selected.${NC} Exiting.\n"
    exit 1;
  fi


  if [ -f "${TF_CONFIG_ROOT}/terraform.tfvars.example.hcl" ]; then
    cp "${TF_CONFIG_ROOT}/terraform.tfvars.example.hcl" "${TFVARS_FILE}"
  else
    touch "${TFVARS_FILE}"
  fi

  ${REPO_CLONE_BASE_DIR}tools/init-tfvars.sh "${TFVARS_FILE}" "${REPO_CLONE_BASE_DIR}" "${DEPLOYMENT_ENV}"
fi

# pattern used to grep for provider at top-level of Terraform configuration
TOP_LEVEL_PROVIDER_PATTERN="^├── provider\[registry.terraform.io/hashicorp"
AWS_PROVIDER_COUNT=$(terraform providers | grep "${TOP_LEVEL_PROVIDER_PATTERN}/aws" | wc -l)
AWS_HOSTED=$(test $AWS_PROVIDER_COUNT -ne 0)
if [ $AWS_HOSTED ]; then
  HOST_PLATFORM="aws"
else
  HOST_PLATFORM="gcp"
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


