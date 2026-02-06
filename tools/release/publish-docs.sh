#!/bin/bash

# Usage:
# ./publish-docs.sh <release> <path-to-repo>
# ./publish-docs.sh v0.5.10 ~/code/psoxy/
# should be fully idempotent 

set -e

# Arguments: repository name and tag name
RELEASE="$1"
PATH_TO_REPO="$2"

GREEN='\e[0;32m'
YELLOW='\e[0;33m'
RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

if [ -z "$RELEASE" ] || [ -z "$PATH_TO_REPO" ]; then
  printf "${RED}Error: Missing required arguments.${NC}\n"
  printf "Usage:\n"
  printf "  ${BLUE}./publish-docs.sh <release> <path-to-repo>${NC}\n"
  printf "Example:\n"
  printf "  ${BLUE}./publish-docs.sh v0.5.10 ~/code/psoxy/${NC}\n"
  exit 1
fi

# Normalize PATH_TO_REPO - remove trailing slash if present
PATH_TO_REPO="${PATH_TO_REPO%/}"

GITBOOK_ORGANIZATION_ID="bJjt4PjVnmXkP0Z3ui04"
GITBOOK_PROXY_DOCS_SITE_ID="site_m0IOi"

GREEN='\e[0;32m'
YELLOW='\e[0;33m'
RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

# ensure jq is installed
if ! command -v jq &> /dev/null; then
  printf "${RED}jq could not be found. Please install it. Exiting.${NC}\n"
  exit 1
fi

if [ ! -f "${PATH_TO_REPO}/java/pom.xml" ]; then
  printf "${RED}${PATH_TO_REPO}/java/pom.xml not found. set <path-to-repo> argument to point to the root of a psoxy checkout. Exiting.${NC}\n"
  exit 1
fi

# use GITBOOK_API_TOKEN, if it exists; else prompt for it
if [ -z "$GITBOOK_API_TOKEN" ]; then
  read -s -p "Enter your GitBook API token: " GITBOOK_API_TOKEN
  echo # print newline after hidden input
fi

# check if docs-$RELEASE branch exists
if git rev-parse --verify "docs-$RELEASE" >/dev/null 2>&1; then
  printf "${YELLOW}Branch docs-${RELEASE} already exists.${NC}\n"
else
  git checkout -b "docs-$RELEASE"
  git push origin "docs-$RELEASE"
  git checkout main
fi

NUMERIC_RELEASE=$(echo $RELEASE | sed 's/v//')
# check if a gitbook space exists with title $RELEASE; create if not

# TODO: we're assuming any space we're concerned with (current or prior version), if exist, has been added to the site; and 2) note more than 1000 spaces in the site
# if that no longer holds we must paginate
SPACES_LIST=$(curl -s https://api.gitbook.com/v1/orgs/${GITBOOK_ORGANIZATION_ID}/sites/${GITBOOK_PROXY_DOCS_SITE_ID}/site-spaces?limit=1000 -H "Authorization: Bearer ${GITBOOK_API_TOKEN}")

SPACE_ID=$(echo "$SPACES_LIST" | jq -r 'first(.items[]? | select(.title == "'$NUMERIC_RELEASE'")) | .space.id')
if [ -z "$SPACE_ID" ]; then
  printf "Space with title ${BLUE}${NUMERIC_RELEASE}${NC} does not exist.\n"

  # prompt user for space to duplicate
  read -p "Enter the title of the space to duplicate: (eg, numeric version of previous release, eg, 0.5.9)" SPACE_TO_DUPLICATE

  SPACE_ID_TO_DUPLICATE=$(echo "$SPACES_LIST" | jq -r 'first(.items[]? | select(.title == "'$SPACE_TO_DUPLICATE'")) | .space.id')
  if [ -z "$SPACE_ID_TO_DUPLICATE" ]; then
    printf "${RED}Space with title ${SPACE_TO_DUPLICATE} does not exist.${NC}\n"
    exit 1
  fi

  printf "Creating a new space with title ${BLUE}${NUMERIC_RELEASE}${NC}...\n"
  # https://api.gitbook.com/v1/spaces/{spaceId}/duplicate
  SPACE_ID=$(curl -s -X POST https://api.gitbook.com/v1/spaces/${SPACE_ID_TO_DUPLICATE}/duplicate -H "Authorization: Bearer ${GITBOOK_API_TOKEN}" | jq -r '.id')
  printf "New space ID: ${GREEN}${SPACE_ID}${NC}\n"

  # update the title of the new space
  curl -s -X PATCH https://api.gitbook.com/v1/spaces/${SPACE_ID} -H "Authorization: Bearer ${GITBOOK_API_TOKEN}" -H "Content-Type: application/json" -d '{"title": "'$NUMERIC_RELEASE'", "parent": "'$GITBOOK_PROXY_DOCS_SITE_ID'"}'
else
  printf "Space with title ${NUMERIC_RELEASE} already exists.\n"
  SPACE_ID=$(echo "$SPACES_LIST" | jq -r 'first(.items[]? | select(.title == "'$NUMERIC_RELEASE'")) | .space.id')
fi

# check if the space has github sync enabled
GITHUB_SYNC_URL=$(curl -s https://api.gitbook.com/v1/spaces/${SPACE_ID} -H "Authorization: Bearer ${GITBOOK_API_TOKEN}" | jq -r '.gitSync.url')
if [ -z "$GITHUB_SYNC_URL" ]; then
  printf "\nEnabling GitHub sync for space ${BLUE}${SPACE_ID}${NC} to branch ${BLUE}docs-${RELEASE}${NC}...\n"
  # patch it to have github sync enabled to target branch
  curl -s -X PATCH https://api.gitbook.com/v1/spaces/${SPACE_ID} -H "Authorization: Bearer ${GITBOOK_API_TOKEN}" -H "Content-Type: application/json" -d '{"gitSync": {"installationProvider": "github", "integration": "github", "url": "https://github.com/Worklytics/psoxy/blob/docs-'$RELEASE'"}' | jq .
else
  printf "\nGitHub sync is already enabled for space ${BLUE}${SPACE_ID}${NC} to ${BLUE}${GITHUB_SYNC_URL}${NC}.\n"
fi

printf "${YELLOW}NOTE: Although GitHub sync appears enabled in API data, recommend re-enable via the Gitbook UX anyways; relying on github sync after copy doesn't seem reliable${NC}\n"

# if not already added to the site, add it
SITE_SPACE_ID=$(echo "$SPACES_LIST" | jq -r 'first(.items[]? | select(.space.id == "'$SPACE_ID'")) | .id')
if [ -z "$SITE_SPACE_ID" ]; then
  printf "Adding space ${BLUE}${SPACE_ID}${NC} to site ${BLUE}${GITBOOK_PROXY_DOCS_SITE_ID}${NC}...\n"
  SITE_SPACE_ID=$(curl -s -X POST https://api.gitbook.com/v1/orgs/${GITBOOK_ORGANIZATION_ID}/sites/${GITBOOK_PROXY_DOCS_SITE_ID}/site-spaces -H "Authorization: Bearer ${GITBOOK_API_TOKEN}" -H "Content-Type: application/json" -d '{"spaceId": "'$SPACE_ID'"}' | jq -r '.id')
else
  printf "Space ${BLUE}${SPACE_ID}${NC} is already added to site ${BLUE}${GITBOOK_PROXY_DOCS_SITE_ID}${NC} (site space id ${BLUE}${SITE_SPACE_ID}${NC}).\n"
fi

# update it to be the default
PATCHED_SITE_SPACE=$(curl -s -X PATCH https://api.gitbook.com/v1/orgs/${GITBOOK_ORGANIZATION_ID}/sites/${GITBOOK_PROXY_DOCS_SITE_ID}/site-spaces/${SITE_SPACE_ID} -H "Authorization: Bearer ${GITBOOK_API_TOKEN}" -H "Content-Type: application/json" -d '{"default": "true"}')
if [ $? -ne 0 ]; then
  printf "${RED}Failed to update site space ${BLUE}${SITE_SPACE_ID}${NC} to be the default.${NC}\n"
  exit 1
fi

printf "Resulting site space:\n"
echo "$PATCHED_SITE_SPACE" | jq .

# open the space in the browser
open https://app.gitbook.com/o/${GITBOOK_ORGANIZATION_ID}/s/${SPACE_ID}/