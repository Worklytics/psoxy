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

find . -type f -name "*.tf" -exec sed -i .bck "${PATTERN}" {} +
rm *.bck

terraform init

printf "Terraform module versions upgraded to ${GREEN}${NEXT_RELEASE}${NC}.\n"
printf "To revert: ${BLUE}$0 ${CURRENT_RELEASE}${NC}\n"
