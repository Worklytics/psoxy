#!/bin/bash

# prepares a bundle of Terraform configuration files to send for review

# Usage: ./make-review-bundle.sh <path-to-terraform-configuration>

# colors
RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

# Function to prompt the user
prompt_continue() {
    local prompt_text="$1"
    while true; do
        read -rp "$prompt_text [Y/n]: " yn
        case $yn in
            [Yy]|[Yy][Ee][Ss] ) break;;
            [Nn]* ) exit;;
            "" ) break;; # Default to Y if blank
            * ) echo "Please answer Y or n.";;
        esac
    done
}

TERRAFORM_CONFIG_PATH=$1

if [ -z "$TERRAFORM_CONFIG_PATH" ]; then
  printf "Usage: ./make-review-bundle.sh <path-to-terraform-configuration>\n"
  exit 1
fi

# if doesn't end with /, add it
if [[ "$TERRAFORM_CONFIG_PATH" != */ ]]; then
  TERRAFORM_CONFIG_PATH="${TERRAFORM_CONFIG_PATH}/"
fi

if [ ! -d "$TERRAFORM_CONFIG_PATH" ]; then
  printf "${RED}Directory doesn't exist: $TERRAFORM_CONFIG_PATH\n${NC}"
  exit 1
fi

printf "This script will prepare a Terraform plan based on your configuration in "
printf "${BLUE}${TERRAFORM_CONFIG_PATH}${NC}, and bundle it along with the relevant files "
printf "(${BLUE}.tf${NC}, ${BLUE}.tfvars${NC}), into a zip file you can send for review.\n"
printf "Your Terraform state file, if any, will not be included.\n"

prompt_continue "Continue?"

DATE_QUALIFIER=$(date +'%Y-%m-%d_%H-%M-%S')
TERRAFORM_REVIEW_BUNDLE="psoxy_tf_config_bundle_${DATE_QUALIFIER}.tar.tz"
ERROR_LOG="error_${DATE_QUALIFIER}.log"
PLAN_FILE="plan_for_review_${DATE_QUALIFIER}.out"

FILES_TO_INCLUDE=""
terraform -chdir="$TERRAFORM_CONFIG_PATH" plan -out="$PLAN_FILE" 2>"$ERROR_LOG"
if [ $? -eq 0 ]; then
  FILES_TO_INCLUDE="$FILES_TO_INCLUDE $PLAN_FILE"
  rm "$ERROR_LOG"
else
  mv "$ERROR_LOG" "$TERRAFORM_CONFIG_PATH$ERROR_LOG"

  FILES_TO_INCLUDE="$FILES_TO_INCLUDE $ERROR_LOG"
  printf "${RED}Terraform plan failed.${NC} A log file ${BLUE}${ERROR_LOG}${NC} has been created "
  printf "and added to the review bundle. You can send as-is or review that log "
  printf "file to attempt a fix.${NC}\n"
fi


cd "$TERRAFORM_CONFIG_PATH" || exit 1
tar -czvf "$TERRAFORM_REVIEW_BUNDLE" *.tf *.tfvars $FILES_TO_INCLUDE
cd - || exit 1
mv "${TERRAFORM_CONFIG_PATH}${TERRAFORM_REVIEW_BUNDLE}" .
mv "${TERRAFORM_CONFIG_PATH}${ERROR_LOG}" .

printf "Review bundle created: ${BLUE}${TERRAFORM_REVIEW_BUNDLE}${NC}\n"

