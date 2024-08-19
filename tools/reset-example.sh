#!/bin/bash


# colors
RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color


# warn user that will delete a bunch of files
printf "This script will ${RED}delete${NC} the your local terraform state, variable files, etc, to "
printf "reset to example template prior to ${BLUE}./init${NC} and any terraform init/plan/apply you've done.\n"
printf "If you have ${RED}NOT${NC} committed these files and/or your local changes, they will be lost.\n"
printf "Do you want to continue? (y/N): "
read -r response
if [[ ! "$response" =~ ^([yY][eE][sS]|[yY])$ ]]; then
    printf "Exiting...\n"
    exit 0
fi

# resets example to state prior to `./init`
rm .terraform.lock.hcl 2>/dev/null
rm build 2>/dev/null
rm update-bundle 2>/dev/null
rm psoxy-* 2>/dev/null
rm -rf .terraform 2>/dev/null
rm terraform.tfvars 2>/dev/null
rm terraform.tfstate 2>/dev/null

# restore main.tf, if modified
printf "Restoring ${BLUE}main.tf${NC} configuration file ...\n"
git checkout HEAD -- main.tf

# check source-specific files that may have been deleted
FILES=("msft-365.tf" "msft-365-variables.tf" "google-workspace.tf" "google-workspace-variables.tf")

check_and_restore_file() {
    local file="$1"

    # Check the git status to find out if the file was deleted
    if git status --short | grep -q "^ D $file"; then
        # The file is deleted, restore it from the HEAD
        printf "Configuration file ${BLUE}$file${NC} was deleted, restoring...\n"
        git checkout HEAD -- "$file"

        if [ $? -eq 0 ]; then
            printf "${BLUE}$file${NC} has been successfully restored.\n"
        else
            printf "${RED}Error occurred while restoring '$file'${NC}\n"
            return 1
        fi
    fi
}

# Loop through the files and pass each one to the check_and_restore_file function
for file in "${FILES[@]}"; do
    check_and_restore_file "$file"
done

if [[ -f upgrade-terraform-modules ]]; then
  rm upgrade-terraform-modules
fi


