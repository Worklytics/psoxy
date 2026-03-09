#!/bin/bash

# colors
COLORSCHEME_SH="$(dirname "$0")/set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

# warn user that will delete a bunch of files
printf "This script will ${ERR}delete${NC} the your local terraform state, variable files, etc, to "
printf "reset to example template prior to ${INFO}./init${NC} and any terraform init/plan/apply you've done.\n"
printf "If you have ${ERR}NOT${NC} committed these files and/or your local changes, they will be lost.\n"
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
printf "Restoring ${INFO}main.tf${NC} configuration file ...\n"
git checkout HEAD -- main.tf

# check source-specific files that may have been deleted
FILES=("msft-365.tf" "msft-365-variables.tf" "google-workspace.tf" "google-workspace-variables.tf")

check_and_restore_file() {
    local file="$1"

    # Check the git status to find out if the file was deleted
    if git status --short | grep -q "^ D $file"; then
        # The file is deleted, restore it from the HEAD
        printf "Configuration file ${INFO}$file${NC} was deleted, restoring...\n"
        git checkout HEAD -- "$file"

        if [ $? -eq 0 ]; then
            printf "${INFO}$file${NC} has been successfully restored.\n"
        else
            printf "${ERR}Error occurred while restoring '$file'${NC}\n"
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

