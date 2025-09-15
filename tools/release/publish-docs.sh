#!/bin/bash

# Arguments: repository name and tag name
RELEASE="$1"
PATH_TO_REPO="$2"

GITBOOK_ORGANIZATION_ID="bJjt4PjVnmXkP0Z3ui04"
GITBOOK_PROXY_DOCS_SITE_ID="site_m0IOi"

GREEN='\e[0;32m'
RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color


# {
#   "object": "space",
#   "id": "FnlUT5RGwbkq4X4A3SR2",
#   "title": "0.5.9",
#   "emoji": "1f512",
#   "visibility": "unlisted",
#   "createdAt": "2025-09-12T21:05:06.250Z",
#   "updatedAt": "2025-09-12T21:06:12.000Z",
#   "editMode": "locked",
#   "urls": {
#     "location": "/spaces/FnlUT5RGwbkq4X4A3SR2",
#     "app": "https://app.gitbook.com/o/bJjt4PjVnmXkP0Z3ui04/s/FnlUT5RGwbkq4X4A3SR2/",
#     "published": "https://docs.worklytics.co/psoxy/"
#   },
#   "organization": "bJjt4PjVnmXkP0Z3ui04",
#   "parent": "zNmLX71HMhDnnTstlQbH",
#   "gitSync": {
#     "installationProvider": "github",
#     "integration": "github",
#     "url": "https://github.com/Worklytics/psoxy/blob/docs-v0.5.9",
#     "updatedAt": "2025-09-12T21:06:03.438Z"
#   },
#   "revision": "MctyyuhukBlPtQjO4sQI",
#   "defaultLevel": "inherit",
#   "comments": 0,
#   "changeRequests": 0,
#   "changeRequestsDraft": 0,
#   "changeRequestsOpen": 0,
#   "permissions": {
#     "view": true,
#     "access": true,
#     "admin": true,
#     "viewInviteLinks": true,
#     "edit": true,
#     "triggerGitSync": true,
#     "comment": true,
#     "merge": true,
#     "review": true,
#     "installIntegration": true
#   },
#   "mergeRules": {
#     "type": "inherit"
#   }
# }


# ensure jq is installed
if ! command -v jq &> /dev/null; then
  printf "${RED}jq could not be found. Please install it. Exiting.${NC}\n"
  exit 1
fi

if [ ! -f "${PATH_TO_REPO}java/pom.xml" ]; then
  printf "${RED}${PATH_TO_REPO}java/pom.xml not found. set <path-to-repo> argument to point to the root of a psoxy checkout. Exiting.${NC}\n"
  exit 1
fi

# use GITBOOK_API_TOKEN, if it exists; else prompt for it
if [ -z "$GITBOOK_API_TOKEN" ]; then
  read -p "Enter your GitBook API token: " GITBOOK_API_TOKEN
fi

# check if docs-$RELEASE branch exists
if git rev-parse --verify "docs-$RELEASE" >/dev/null 2>&1; then
  printf "${RED}Branch docs-${RELEASE} already exists.${NC}\n"
else
  git checkout -b "docs-$RELEASE"
  git push origin "docs-$RELEASE"
  git checkout main
fi

# check if a gitbook space exists with title $RELEASE; create if not

SPACE_ID=$(curl https://api.gitbook.com/v1/orgs/${GITBOOK_ORGANIZATION_ID}/spaces -H "Authorization: Bearer ${GITBOOK_API_TOKEN}" | jq -r '.[] | select(.title == "'$RELEASE'") | .id')
if [ -z "$SPACE_ID" ]; then
  printf "Space with title ${RELEASE} does not exist.\n"

  # prompt user for space to duplicate
  read -p "Enter the title of the space to duplicate: (eg, previous release)" SPACE_TO_DUPLICATE

  SPACE_ID_TO_DUPLICATE=$(curl https://api.gitbook.com/v1/orgs/${GITBOOK_ORGANIZATION_ID}/spaces | jq -r '.[] | select(.title == "'$SPACE_TO_DUPLICATE'") | .id')
  if [ -z "$SPACE_ID_TO_DUPLICATE" ]; then
    printf "${RED}Space with title ${SPACE_TO_DUPLICATE} does not exist.${NC}\n"
    exit 1
  fi

  printf "Creating a new space with title ${RELEASE}...\n"
  # https://api.gitbook.com/v1/spaces/{spaceId}/duplicate
  SPACE_ID=$(curl -X POST https://api.gitbook.com/v1/spaces/${SPACE_ID_TO_DUPLICATE}/duplicate -H "Authorization: Bearer ${GITBOOK_API_TOKEN}" | jq -r '.id')
  printf "New space ID: ${GREEN}${SPACE_ID}${NC}\n"

  # update the title of the new space
  curl -X PATCH https://api.gitbook.com/v1/spaces/${SPACE_ID} -H "Authorization: Bearer ${GITBOOK_API_TOKEN}" -H "Content-Type: application/json" -d '{"title": "'$RELEASE'", "parent": "'$GITBOOK_PROXY_DOCS_SITE_ID'"}'
else
  printf "Space with title ${RELEASE} already exists.\n"
  SPACE_ID=$(curl https://api.gitbook.com/v1/orgs/${GITBOOK_ORGANIZATION_ID}/spaces | jq -r '.[] | select(.title == "'$RELEASE'") | .id')
fi

# patch it to have github sync enabled to target branch
curl -X PATCH https://api.gitbook.com/v1/spaces/${SPACE_ID} -H "Authorization: Bearer ${GITBOOK_API_TOKEN}" -H "Content-Type: application/json" -d '{"gitSync": {"installationProvider": "github", "integration": "github", "url": "https://github.com/Worklytics/psoxy/blob/docs-'$RELEASE'"}'

# open the space in the browser
# open https://app.gitbook.com/o/${GITBOOK_ORGANIZATION_ID}/s/${SPACE_ID}/

# add space to the site
SITE_SPACE_ID=$(curl -X POST https://api.gitbook.com/v1/orgs/${GITBOOK_ORGANIZATION_ID}/sites/${GITBOOK_PROXY_DOCS_SITE_ID}/site-spaces -H "Authorization: Bearer ${GITBOOK_API_TOKEN}" -H "Content-Type: application/json" -d '{"spaceId": "'$SPACE_ID'"}' | jq -r '.id')

# update it to be the default
curl -X PATCH ttps://api.gitbook.com/v1/orgs/${GITBOOK_ORGANIZATION_ID}/sites/${GITBOOK_PROXY_DOCS_SITE_ID}/site-spaces/${SITE_SPACE_ID}-H "Authorization: Bearer ${GITBOOK_API_TOKEN}" -H "Content-Type: application/json" -d '{"default": "true"}'