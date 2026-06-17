#!/bin/bash

# Usage ./tools/release/prep.sh <current-release> <next-release>

COLORSCHEME_SH="$(dirname "$0")/../set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

CURRENT_RELEASE=$1
NEXT_RELEASE=$2

if [ -z "$CURRENT_RELEASE" ]; then
  printf "${ERR}Current release version not specified. Exiting.${NC}\n"
  printf "Usage: ${INFO}./tools/release/prep.sh <current-release> <next-release>${NC}\n"
  exit 1
fi

if [ -z "$NEXT_RELEASE" ]; then
  printf "${ERR}Next release version not specified. Exiting.${NC}\n"
  printf "Usage: ${INFO}./tools/release/prep.sh <current-release> <next-release>${NC}\n"
  exit 1
fi

if [ "$NEXT_RELEASE" = "rc-NEXT" ] || [ "$NEXT_RELEASE" = "NEXT" ]; then
  printf "${ERR}Invalid next release '${NEXT_RELEASE}'. Provide an explicit rc branch, e.g. rc-v0.6.5.${NC}\n"
  exit 1
fi

if [[ "$NEXT_RELEASE" =~ ^rc- ]]; then
  if [[ ! "$NEXT_RELEASE" =~ ^rc-v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    printf "${ERR}Invalid rc branch name '${NEXT_RELEASE}'. Expected format: rc-vX.Y.Z (e.g. rc-v0.6.5).${NC}\n"
    exit 1
  fi
  IS_RC=1
else
  IS_RC=0
fi

if [ ! -f "java/pom.xml" ]; then
  printf "${ERR}java/pom.xml not found. You should run this script from root of psoxy checkout. Exiting.${NC}\n"
  exit 1
fi

if [ "$IS_RC" -eq 1 ]; then
  printf "Preparing release candidate ${SUCCESS}${NEXT_RELEASE}${NC} ...\n"
  CURRENT_BRANCH=$(git branch --show-current)

  # check if current branch is clean
  if [ -n "$(git status --porcelain)" ]; then
    printf "${ERR}Current branch is not clean. Please commit or stash your changes and try again to prepare release candidate.${NC}\n"
    exit 1
  fi

  if [ "$CURRENT_BRANCH" != "main" ]; then
    printf "${ERR}Current branch is not main. Please checkout main branch and try again to prepare release candidate.${NC}\n"
    exit 1
  fi

  git checkout -b "$NEXT_RELEASE"
else
  printf "Preparing release ${SUCCESS}${NEXT_RELEASE}${NC} ...\n"
fi

CURRENT_RELEASE_PATTERN=$(echo $CURRENT_RELEASE | sed 's/\./\\\./g')
PATTERN="s/ref=${CURRENT_RELEASE_PATTERN}/ref=${NEXT_RELEASE}/"

find infra/ -type f -name "*.tf" -exec sed -i .bck $PATTERN {} +

# delete the sed backup files
find infra/ -type f -name "*.bck" -exec rm {} +

# deal with pom.xml
CURRENT_RELEASE_NUMBER=$(echo $CURRENT_RELEASE | sed 's/[^0-9\.]//g')
NEXT_RELEASE_NUMBER=$(echo $NEXT_RELEASE | sed 's/[^0-9\.]//g')
printf "Next release number: ${INFO}${NEXT_RELEASE_NUMBER}${NC}\n"
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
printf "The following files still contain references to the current release ${SUCCESS}${CURRENT_RELEASE}${NC}; please review:\n"
git grep -l "$CURRENT_RELEASE_PATTERN" java/
git grep -l "$CURRENT_RELEASE_PATTERN" infra/
git grep -l "$CURRENT_RELEASE_PATTERN" tools/

./tools/release/lib/sync-examples.sh ./

# Generate SBOMs
printf "Generate updated SBOMs for AWS and GCP?\n"
read -p "(Y/n) " -n 1 -r
REPLY=${REPLY:-Y}
echo    # Move to a new line
case "$REPLY" in
  [yY][eE][sS]|[yY])
    ./tools/release/generate-sbom.sh
    git add docs/aws/sbom.json
    git add docs/gcp/sbom.json
    ;;
  *)
    echo "SBOM generation skipped."
    ;;
esac
echo "" # newline

git add java/pom.xml
git add infra/examples-dev/
git add tools/init-tfvars.sh
git add -u java/
git add -u tools/

git status

COMMIT_MESSAGE="update release refs to ${NEXT_RELEASE}"

printf "See above for status. Commit changes with message ${INFO}${COMMIT_MESSAGE}${NC}?\n"

read -p "(Y/n) " -n 1 -r
REPLY=${REPLY:-Y}
echo    # Move to a new line
case "$REPLY" in
  [yY][eE][sS]|[yY])
    git commit -m "$COMMIT_MESSAGE"
    git push origin "$NEXT_RELEASE"
    ;;
  *)
    echo "Changes not committed. Exiting."
    exit 0
    ;;
esac
echo "" # newline

if [ "$IS_RC" -eq 1 ]; then
  printf "Update open PRs to point to ${SUCCESS}${NEXT_RELEASE}${NC}?\n"
  read -p "(Y/n) " -n 1 -r
  REPLY=${REPLY:-Y}
  echo    # Move to a new line
  case "$REPLY" in
    [yY][eE][sS]|[yY])
      ./tools/release/lib/update-open-prs.sh "$NEXT_RELEASE"
      ;;
    *)
      echo "open PRs not updated."
      ;;
  esac
  echo "" # newline
else
  printf "Open PR to merge rc back to main?\n"
  read -p "(Y/n) " -n 1 -r
  REPLY=${REPLY:-Y}
  echo    # Move to a new line
  case "$REPLY" in
    [yY][eE][sS]|[yY])
      ./tools/release/rc-to-main.sh "$NEXT_RELEASE"
      ;;
    *)
      echo "No PR opened."
      ;;
  esac
fi
