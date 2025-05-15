#!/bin/bash

# Arguments: repository name and tag name
RELEASE="$1"

GREEN='\e[0;32m'
RED='\e[0;31m'
NC='\e[0m' # No Color



if [ ! -f "${PATH_TO_REPO}java/pom.xml" ]; then
  printf "${RED}${PATH_TO_REPO}java/pom.xml not found. set <path-to-repo> argument to point to the root of a psoxy checkout. Exiting.${NC}\n"
  exit 1
fi

CURRENT_RELEASE_NUMBER=$(sed -n 's|[[:space:]]*<revision>\(.*\)</revision>|\1|p' "${PATH_TO_REPO}java/pom.xml" )

if [ -z "$RELEASE" ]; then
  RELEASE="v${CURRENT_RELEASE_NUMBER}"
else
  # maintain compatibility with current interface
  if [ "$RELEASE" != "v${CURRENT_RELEASE_NUMBER}" ]; then
    printf "${RED}Release version provided, ${RELEASE}, does not match current release version in pom.xml, ${CURRENT_RELEASE_NUMBER}. Exiting.${NC}\n"
    exit 1
  fi
fi

# die on error
set -e

git fetch origin

if git rev-parse "$RELEASE" >/dev/null 2>&1; then
  printf "Tag ${GREEN}$RELEASE${NC} already exists.\n"
else
  git checkout main

  git pull origin main

  # verify on main branch and clean status
  CURRENT_BRANCH=$(git branch --show-current)
  if [ "$CURRENT_BRANCH" != "main" ]; then
    printf "${RED}Current branch is not main. Please checkout main branch and try again.${NC}\n"
    exit 1
  fi
  printf "Tagging ${GREEN}$RELEASE${NC} ...\n"
  git tag $RELEASE
fi

printf "Pushing tag ${GREEN}$RELEASE${NC} to origin ...\n"
git push origin $RELEASE

if gh release view $RELEASE >/dev/null 2>&1
then
  printf "Release ${GREEN}$RELEASE${NC} already exists.\n"
else
  printf "Creating release ${GREEN}$RELEASE${NC} in GitHub ...\n"
  gh release create --draft --generate-notes $RELEASE
fi

printf "Updating release notes for ${GREEN}$RELEASE${NC} ...\n"
# Fetch release notes
release_notes=$(gh release view $RELEASE --json body -q '.body')

# Remove GitHub username mentions
modified_notes=$(echo -e "$release_notes" | sed -r 's/by @[a-zA-Z0-9_-]+ in//g')

# Update release notes
gh release edit $RELEASE -n "$modified_notes"

# check if rc branch exists, and offer to delete if so
rc_branch="rc-$RELEASE"
if git rev-parse "$rc_branch" >/dev/null 2>&1; then
  printf "Delete the ${BLUE}rc-${RELEASE}${NC} branch?\n"
  read -p "(Y/n) " -n 1 -r
  REPLY=${REPLY:-Y}
  echo    # Move to a new line
  case "$REPLY" in
    [yY][eE][sS]|[yY])
       git branch -d "rc-$RELEASE"
      ;;
    *)
      printf "Skipped deletion of ${BLUE}rc-$RELEASE${NC}\n"
      ;;
  esac
fi

printf "Opening release ${BLUE}${RELEASE}${NC} in browser; review / update notes and then publish as latest ...\n"
gh release view $RELEASE --web

printf "Do you want to create a docs branch based on the release? (Y/n)\n"
read -p "(Y/n) " -n 1 -r
REPLY=${REPLY:-Y}
echo    # Move to a new line
if [[ "$REPLY" =~ ^[Yy][Ee]?[Ss]?$ ]]; then
 if git rev-parse --verify "docs-$RELEASE" >/dev/null 2>&1; then
    printf "${RED}Branch docs-${RELEASE} already exists.${NC}\n"
  else
    git checkout -b "docs-$RELEASE"
    git push origin "docs-$RELEASE"
    git checkout main
    printf "Docs branch ${GREEN}docs-$RELEASE${NC} created and pushed. View it at: ${BLUE}https://github.com/Worklytics/psoxy/tree/docs-$RELEASE${NC}\n"
    printf "Manual steps to publish docs in GitBook: \n"
    printf "1. Login to gitbook ( ${BLUE}https://app.gitbook.com/o/bJjt4PjVnmXkP0Z3ui04/sites/site_m0IOi${NC} ) \n"
    printf "2. Navigtate to 'Structure -> Manage'  under the Psoxy site \n"
    printf "3. Create a new 'variant' in the list (copy existing), with the same name as the release ${GREEN}X.Y.Z${NC}\n"
    printf "4. Click on the new variant's link icon; edit GitHub Sync settings to point to the branch: ${GREEN}docs-$RELEASE${NC}\n"
    printf "5. Set the new 'variant' as the 'default' for the site\n"
    printf "6. Visit ${BLUE}https://docs.worklytics.co/psoxy${NC} to confirm it points to the latest release\n"
  fi
fi

printf "  Then update example templates to point to it:\n"
if [ -d ~/code/psoxy-example-aws/ ]; then
  AWS_EXAMPLE_DIR="~/code/psoxy-example-aws"
else
  AWS_EXAMPLE_DIR="~/psoxy-example-aws"
fi

if [ -d ~/code/psoxy-example-gcp/ ]; then
  GCP_EXAMPLE_DIR="~/code/psoxy-example-gcp"
else
  GCP_EXAMPLE_DIR="~/psoxy-example-gcp"
fi

printf "    ${BLUE}./tools/release/example-create-release-pr.sh . aws ${AWS_EXAMPLE_DIR}${NC}\n"
printf "    ${BLUE}./tools/release/example-create-release-pr.sh . gcp ${GCP_EXAMPLE_DIR}${NC}\n"
