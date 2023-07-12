#!/bin/bash

# Usage ./tools/release/prep.sh <current-release> <next-release>

RED='\e[0;31m'
GREEN='\e[0;32m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

CURRENT_RELEASE=$1
NEXT_RELEASE=$2

if [ -z "$CURRENT_RELEASE" ]; then
  printf "${RED}Current release version not specified. Exiting.${NC}\n"
  printf "Usage: ${BLUE}./tools/check-release.sh <current-release> <next-release>${NC}\n"
  exit 1
fi

if [ -z "$NEXT_RELEASE" ]; then
  printf "${RED}Next release version not specified. Exiting.${NC}\n"
  printf "Usage: ${BLUE}./tools/check-release.sh <current-release> <next-release>${NC}\n"
  exit 1
fi

if [ ! -f "java/pom.xml" ]; then
  printf "${RED}java/pom.xml not found. You should run this script from root of psoxy checkout. Exiting.${NC}\n"
  exit 1
fi


printf "Preparing release ${GREEN}${NEXT_RELEASE}${NC} ...\n"


CURRENT_RELEASE_PATTERN=$(echo $CURRENT_RELEASE | sed 's/\./\\\./g')
PATTERN="s/ref=${CURRENT_RELEASE_PATTERN}/ref=${NEXT_RELEASE}/"

find infra/ -type f -name "*.tf" -exec sed -i .bck $PATTERN {} +

# delete the sed backup files
find infra/ -type f -name "*.bck" -exec rm {} +

# deal with pom.xml
CURRENT_RELEASE_NUMBER=$(echo $CURRENT_RELEASE | sed 's/[^0-9\.]//g')
NEXT_RELEASE_NUMBER=$(echo $NEXT_RELEASE | sed 's/[^0-9\.]//g')
printf "Next release number: ${BLUE}${NEXT_RELEASE_NUMBER}${NC}\n"
RELEASE_NUMBER_PATTERN="s/<revision>$(echo $CURRENT_RELEASE_NUMBER | sed 's/\./\\\./g')<\/revision>/<revision>$(echo $NEXT_RELEASE_NUMBER | sed 's/\./\\\./g')\<\/revision>/"
sed -i .bck $RELEASE_NUMBER_PATTERN java/pom.xml
rm java/pom.xml.bck

# deal with java code
RELEASE_REF_PATTERN="s/\"$(echo $CURRENT_RELEASE | sed 's/\./\\\./g')\"/\"$(echo $NEXT_RELEASE | sed 's/\./\\\./g')\"/"
find java/ -type f -name "*.java" -exec sed -i .bck $RELEASE_REF_PATTERN {} +
find java/ -type f -name "*.bck" -exec rm {} +

# tools
find tools/ -type f -name "*.sh" -exec sed -i .bck $RELEASE_REF_PATTERN {} +
find tools/ -type f -name "*.bck" -exec rm {} +

# check for remaining references to current release
printf "The following files still contain references to the current release ${GREEN}${CURRENT_RELEASE}${NC}; please review:\n"
git grep -l "$CURRENT_RELEASE_PATTERN" java/
git grep -l "$CURRENT_RELEASE_PATTERN" infra/
git grep -l "$CURRENT_RELEASE_PATTERN" tools/

git add java/
git add infra/examples/**/main.tf
git add infra/examples-dev/**/main.tf
git add infra/examples-dev/**/msft-365.tf
git add infra/examples-dev/**/google-workspace.tf