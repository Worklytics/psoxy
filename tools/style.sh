#!/bin/bash

COLORSCHEME_SH="$(dirname "$0")/set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

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
    printf "${ERR}No infra/ directory found; this script expects to run at root of repo.${NC}\n"
    exit 1
fi

# run terraform fmt -recursive within infra directory
cd infra
printf "Running ${INFO}terraform fmt -recursive ${NC}...\n"
terraform fmt -recursive

cd -

if [ ! -d "docs" ]; then
    printf "${ERR}No docs/ directory found; this script expects to run at root of repo.${NC}\n"
    exit 1
fi

prettier --write "docs/**/*.md"
