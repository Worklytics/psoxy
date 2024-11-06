#!/bin/bash


RED="\e[31m"
BLUE="\e[34m"
NC="\e[0m"


# check if prettier installed, if not, offer to install it
if ! command -v prettier &> /dev/null
then
    printf "prettier could not be found. Do you want to install it? (Y/n) "
    read -n 1 -r
    REPLY=${REPLY:-Y}
    echo    # Move to a new line
    case "$REPLY" in
        [yY][eE][sS]|[yY])
            npm install --global prettier
            ;;
        *)
            printf "Skipped installation of prettier\n"
            exit 1
            ;;
    esac
fi

# verify in root of psoxy repo
if [ ! -f "README.md" ]; then
    printf "This script should be run from the root of the psoxy repo.\n"
    exit 1
fi

if [ ! -d "infra" ]; then
    printf "${RED}No infra/ directory found; this script expects to run at root of repo.${NC}\n"
    exit 1
fi

# run terraform fmt -recursive within infra directory
cd infra
printf "Running ${BLUE}terraform fmt -recursive ${NC}...\n"
terraform fmt -recursive

cd -

if [ ! -d "docs" ]; then
    printf "${RED}No docs/ directory found; this script expects to run at root of repo.${NC}\n"
    exit 1
fi

prettier --write "docs/**/*.md"
