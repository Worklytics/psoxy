#!/bin/bash

# usage:
# ./tools/upgrade-tf-modules.sh <next-release> <path-to-clone>
# ./tools/upgrade-tf-modules.sh v0.4.30 ~/code/psoxy/
#
# NOTE: this is called 'upgrade', but in principal it can be used to downgrade as well. But in
# general proxy terraform modules aren't built to support downgrading, so YMMV

NEXT_RELEASE=$1

# colors
RED='\e[0;31m'
BLUE='\e[0;34m'
GREEN='\e[0;32m'
NC='\e[0m' # No Color

CURRENT_RELEASE=$(sed -n '/git::https:\/\/github\.com\/worklytics\/psoxy\//{s/.*ref=\([^"&]*\).*/\1/p;q;}' main.tf)

# if $NEXT_RELEASE is not provided, warn user and exit
if [ -z "$NEXT_RELEASE" ]; then
  printf "${RED}Next release version not specified. Exiting.${NC}\n"
  exit 1
fi

printf "Parsed your current terraform module version as ${BLUE}${CURRENT_RELEASE}${NC}; this script will upgrade it to ${GREEN}${NEXT_RELEASE}${NC}?\n"

read -p "Do you wish to continue? (Y/n) " -n 1 -r
REPLY=${REPLY:-Y}
echo "" # newline

case "$REPLY" in
  [yY][eE][sS]|[yY])
    echo "Applying upgrade ..."
    ;;
  [nN]|[oO])
    echo "Aborted."
    exit 0
    ;;
  *)
    printf "${RED}Invalid input${NC}\n"
    exit 1
    ;;
esac


CURRENT_RELEASE_PATTERN=$(echo $CURRENT_RELEASE | sed 's/\./\\\./g')
PATTERN="s|ref=${CURRENT_RELEASE_PATTERN}|ref=${NEXT_RELEASE}|"

find . -type f -name "*.tf" -exec sed -i.bck "${PATTERN}" {} +
rm *.bck

terraform init

printf "Terraform module versions upgraded to ${GREEN}${NEXT_RELEASE}${NC}.\n"
printf "To revert: ${BLUE}$0 ${CURRENT_RELEASE}${NC}\n"

if grep -q '^deployment_bundle\s*=' terraform.tfvars; then
    # Prompt user
    printf "Your ${BLUE}terraform.tfvars${NC} file references a pre-built 'deployment_bundle' bundle."
    read -p "Do you want to run './update-bundle' to re-build it with a version matching the terraform modules you just updated? [Y/n] " response

    # If user presses Enter without input, default to 'Y'
    if [[ -z "$response" || "$response" =~ ^[Yy]$ ]]; then
        ./update-bundle
    else
        echo "Bundle not updated."
    fi
fi


# parse NEXT_RELEASE as something of the form `rc-v0.5.6` or `v0.5.6`, as MAJOR.MINOR.PATCH
NEXT_MAJOR=$(echo $NEXT_RELEASE | sed 's/^v\([0-9]*\)\.\([0-9]*\)\.\([0-9]*\)$/\1/')
NEXT_MINOR=$(echo $NEXT_RELEASE | sed 's/^v\([0-9]*\)\.\([0-9]*\)\.\([0-9]*\)$/\2/')
NEXT_PATCH=$(echo $NEXT_RELEASE | sed 's/^v\([0-9]*\)\.\([0-9]*\)\.\([0-9]*\)$/\3/')

# parse CURRENT_RELEASE as something of the form `rc-v0.5.6` or `v0.5.6`, as MAJOR.MINOR.PATCH
CURRENT_MAJOR=$(echo $CURRENT_RELEASE | sed 's/^v\([0-9]*\)\.\([0-9]*\)\.\([0-9]*\)$/\1/')
CURRENT_MINOR=$(echo $CURRENT_RELEASE | sed 's/^v\([0-9]*\)\.\([0-9]*\)\.\([0-9]*\)$/\2/')
CURRENT_PATCH=$(echo $CURRENT_RELEASE | sed 's/^v\([0-9]*\)\.\([0-9]*\)\.\([0-9]*\)$/\3/')

if [ $NEXT_MINOR -gt $CURRENT_MINOR ]; then
  printf "Next release version *may* include a provider bump. It is recommended to run ${BLUE} terraform init --upgrade${NC} to get the latest versions of all terraform providers that are compatible with your configuration.\n"
  printf "You may first with to run ${BLUE}terraform providers${NC} to review the various provider version constraints, and consider revising them in top-level ${BLUE}main.tf${NC} or wherever they're specified.\n"
fi