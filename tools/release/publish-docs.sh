#!/bin/bash

# Usage:
# ./publish-docs.sh <release> <path-to-repo>
# ./publish-docs.sh v0.5.10 ~/code/psoxy/
# should be fully idempotent 

set -e

# Arguments: repository name and tag name
RELEASE="$1"
PATH_TO_REPO="$2"

COLORSCHEME_SH="$(dirname "$0")/../set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

if [ -z "$RELEASE" ] || [ -z "$PATH_TO_REPO" ]; then
  printf "${ERR}Error: Missing required arguments.${NC}\n"
  printf "Usage:\n"
  printf "  ${INFO}./publish-docs.sh <release> <path-to-repo>${NC}\n"
  printf "Example:\n"
  printf "  ${INFO}./publish-docs.sh v0.5.10 ~/code/psoxy/${NC}\n"
  exit 1
fi

# Normalize PATH_TO_REPO - remove trailing slash if present
PATH_TO_REPO="${PATH_TO_REPO%/}"

GITBOOK_ORGANIZATION_ID="bJjt4PjVnmXkP0Z3ui04"
GITBOOK_PROXY_DOCS_SITE_ID="site_m0IOi"

# ensure jq is installed
if ! command -v jq &> /dev/null; then
  printf "${ERR}jq could not be found. Please install it. Exiting.${NC}\n"
  exit 1
fi

if [ ! -f "${PATH_TO_REPO}/java/pom.xml" ]; then
  printf "${ERR}${PATH_TO_REPO}/java/pom.xml not found. set <path-to-repo> argument to point to the root of a psoxy checkout. Exiting.${NC}\n"
  exit 1
fi

# use GITBOOK_API_TOKEN, if it exists; else prompt for it
if [ -z "$GITBOOK_API_TOKEN" ]; then
  read -s -p "Enter your GitBook API token: " GITBOOK_API_TOKEN
  echo # print newline after hidden input
fi

# check if docs-$RELEASE branch exists
if git rev-parse --verify "docs-$RELEASE" >/dev/null 2>&1; then
  printf "${WARN}Branch docs-${RELEASE} already exists.${NC}\n"
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
  printf "Space with title ${INFO}${NUMERIC_RELEASE}${NC} does not exist.\n"

  SPACE_TO_DUPLICATE=""
  # Try to guess previous patch release (e.g., 0.5.17 -> 0.5.16)
  IFS='.' read -ra VERSION_PARTS <<< "$NUMERIC_RELEASE"
  MAJOR="${VERSION_PARTS[0]}"
  MINOR="${VERSION_PARTS[1]}"
  PATCH="${VERSION_PARTS[2]}"

  if [ -n "$PATCH" ] && [ "$PATCH" -gt 0 ] 2>/dev/null; then
    PREV_PATCH=$((PATCH - 1))
    GUESSED_PREV="${MAJOR}.${MINOR}.${PREV_PATCH}"
    
    # check if guessed space exists
    GUESSED_SPACE_ID=$(echo "$SPACES_LIST" | jq -r 'first(.items[]? | select(.title == "'$GUESSED_PREV'")) | .space.id')
    if [ -n "$GUESSED_SPACE_ID" ] && [ "$GUESSED_SPACE_ID" != "null" ]; then
      printf "Found previous patch release space ${BLUE}${GUESSED_PREV}${NC}. Using it to duplicate.\n"
      SPACE_TO_DUPLICATE="$GUESSED_PREV"
      SPACE_ID_TO_DUPLICATE="$GUESSED_SPACE_ID"
    fi
  fi

  if [ -z "$SPACE_TO_DUPLICATE" ]; then
    # prompt user for space to duplicate
    read -p "Enter the title of the space to duplicate: (eg, numeric version of previous release, eg, 0.5.9) " SPACE_TO_DUPLICATE
  
    SPACE_ID_TO_DUPLICATE=$(echo "$SPACES_LIST" | jq -r 'first(.items[]? | select(.title == "'$SPACE_TO_DUPLICATE'")) | .space.id')
  fi

  if [ -z "$SPACE_ID_TO_DUPLICATE" ] || [ "$SPACE_ID_TO_DUPLICATE" = "null" ]; then
    printf "${ERR}Space with title ${SPACE_TO_DUPLICATE} does not exist.${NC}\n"
    exit 1
  fi

  printf "Creating a new space with title ${INFO}${NUMERIC_RELEASE}${NC}...\n"
  # https://api.gitbook.com/v1/spaces/{spaceId}/duplicate
  SPACE_ID=$(curl -s -X POST https://api.gitbook.com/v1/spaces/${SPACE_ID_TO_DUPLICATE}/duplicate -H "Authorization: Bearer ${GITBOOK_API_TOKEN}" | jq -r '.uid // .id // empty')
  printf "New space ID: ${SUCCESS}${SPACE_ID}${NC}\n"

  printf "${YELLOW}Waiting 10 seconds for GitBook permissions to propagate to the new space...${NC}\n"
  sleep 10

  # update the title of the new space
  PATCH_RES=$(curl -s -X PATCH https://api.gitbook.com/v1/spaces/${SPACE_ID} -H "Authorization: Bearer ${GITBOOK_API_TOKEN}" -H "Content-Type: application/json" -d '{"title": "'$NUMERIC_RELEASE'"}')
  if echo "$PATCH_RES" | grep -q '"error"'; then
    printf "${RED}Error updating space title:${NC}\n"
    echo "$PATCH_RES" | jq .
    printf "${YELLOW}Retrying after 5 more seconds...${NC}\n"
    sleep 5
    PATCH_RES=$(curl -s -X PATCH https://api.gitbook.com/v1/spaces/${SPACE_ID} -H "Authorization: Bearer ${GITBOOK_API_TOKEN}" -H "Content-Type: application/json" -d '{"title": "'$NUMERIC_RELEASE'"}')
    if echo "$PATCH_RES" | grep -q '"error"'; then
      printf "${RED}Failed again on updating space title:${NC}\n"
      echo "$PATCH_RES" | jq .
    fi
  fi
else
  printf "Space with title ${NUMERIC_RELEASE} already exists.\n"
  SPACE_ID=$(echo "$SPACES_LIST" | jq -r 'first(.items[]? | select(.title == "'$NUMERIC_RELEASE'")) | .space.id')
fi

# check if the space has github sync enabled
GITHUB_SYNC_URL=$(curl -s https://api.gitbook.com/v1/spaces/${SPACE_ID} -H "Authorization: Bearer ${GITBOOK_API_TOKEN}" | jq -r '.gitSync.url')
if [ -z "$GITHUB_SYNC_URL" ]; then
  printf "\nEnabling GitHub sync for space ${INFO}${SPACE_ID}${NC} to branch ${INFO}docs-${RELEASE}${NC}...\n"
  # patch it to have github sync enabled to target branch
  curl -s -X PATCH https://api.gitbook.com/v1/spaces/${SPACE_ID} -H "Authorization: Bearer ${GITBOOK_API_TOKEN}" -H "Content-Type: application/json" -d '{"gitSync": {"installationProvider": "github", "integration": "github", "url": "https://github.com/Worklytics/psoxy/blob/docs-'$RELEASE'"}' | jq .
else
  printf "\nGitHub sync is already enabled for space ${INFO}${SPACE_ID}${NC} to ${INFO}${GITHUB_SYNC_URL}${NC}.\n"
fi

printf "${WARN}NOTE: Although GitHub sync appears enabled in API data, recommend re-enable via the Gitbook UX anyways; relying on github sync after copy doesn't seem reliable${NC}\n"

# if not already added to the site, add it
SITE_SPACE_ID=$(echo "$SPACES_LIST" | jq -r 'first(.items[]? | select(.space.id == "'$SPACE_ID'")) | (.uid // .id // empty)')
if [ -z "$SITE_SPACE_ID" ] || [ "$SITE_SPACE_ID" = "null" ]; then
  printf "Adding space ${INFO}${SPACE_ID}${NC} to site ${INFO}${GITBOOK_PROXY_DOCS_SITE_ID}${NC}...\n"
  POST_SITE_SPACE_RES=$(curl -s -X POST https://api.gitbook.com/v1/orgs/${GITBOOK_ORGANIZATION_ID}/sites/${GITBOOK_PROXY_DOCS_SITE_ID}/site-spaces -H "Authorization: Bearer ${GITBOOK_API_TOKEN}" -H "Content-Type: application/json" -d '{"space": "'$SPACE_ID'"}')
  SITE_SPACE_ID=$(echo "$POST_SITE_SPACE_RES" | jq -r '.uid // .id // empty')
  
  if [ -z "$SITE_SPACE_ID" ] || [ "$SITE_SPACE_ID" = "null" ]; then
     # maybe it uses spaceId? rollback and retry
     printf "${WARNING}Trying with spaceId payload...${NC}\n"
     POST_SITE_SPACE_RES=$(curl -s -X POST https://api.gitbook.com/v1/orgs/${GITBOOK_ORGANIZATION_ID}/sites/${GITBOOK_PROXY_DOCS_SITE_ID}/site-spaces -H "Authorization: Bearer ${GITBOOK_API_TOKEN}" -H "Content-Type: application/json" -d '{"spaceId": "'$SPACE_ID'"}')
     SITE_SPACE_ID=$(echo "$POST_SITE_SPACE_RES" | jq -r '.uid // .id // empty')
  fi

  if [ -z "$SITE_SPACE_ID" ] || [ "$SITE_SPACE_ID" = "null" ]; then
    printf "${ERR}Failed to add space to site. Response:${NC}\n"
    echo "$POST_SITE_SPACE_RES" | jq .
    printf "${WARNING}Retrying POST site space in 5 seconds...${NC}\n"
    sleep 5
    POST_SITE_SPACE_RES=$(curl -s -X POST https://api.gitbook.com/v1/orgs/${GITBOOK_ORGANIZATION_ID}/sites/${GITBOOK_PROXY_DOCS_SITE_ID}/site-spaces -H "Authorization: Bearer ${GITBOOK_API_TOKEN}" -H "Content-Type: application/json" -d '{"space": "'$SPACE_ID'"}')
    SITE_SPACE_ID=$(echo "$POST_SITE_SPACE_RES" | jq -r '.uid // .id // empty')
    if [ -z "$SITE_SPACE_ID" ] || [ "$SITE_SPACE_ID" = "null" ]; then
        printf "${ERR}Failed again to add space to site. Giving up. Response:${NC}\n"
        echo "$POST_SITE_SPACE_RES" | jq .
        exit 1
    fi
  fi
else
  printf "Space ${INFO}${SPACE_ID}${NC} is already added to site ${INFO}${GITBOOK_PROXY_DOCS_SITE_ID}${NC} (site space id ${INFO}${SITE_SPACE_ID}${NC}).\n"
fi

# update it to be the default
PATCHED_SITE_SPACE=$(curl -s -X PATCH https://api.gitbook.com/v1/orgs/${GITBOOK_ORGANIZATION_ID}/sites/${GITBOOK_PROXY_DOCS_SITE_ID}/site-spaces/${SITE_SPACE_ID} -H "Authorization: Bearer ${GITBOOK_API_TOKEN}" -H "Content-Type: application/json" -d '{"default": "true"}')
if [ $? -ne 0 ]; then
  printf "${ERR}Failed to update site space ${INFO}${SITE_SPACE_ID}${NC} to be the default.${NC}\n"
  exit 1
fi

printf "Resulting site space:\n"
echo "$PATCHED_SITE_SPACE" | jq .

# open the space in the browser
open https://app.gitbook.com/o/${GITBOOK_ORGANIZATION_ID}/s/${SPACE_ID}/