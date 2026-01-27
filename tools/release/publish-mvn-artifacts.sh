#!/bin/bash

GREEN='\e[0;32m'
RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

PATH_TO_REPO="$1"

if [ -z "$PATH_TO_REPO" ]; then
  printf "${RED}Error: PATH_TO_REPO is required.${NC}\n"
  exit 1
fi

if [ ! -f "${PATH_TO_REPO}java/pom.xml" ]; then
  printf "${RED}${PATH_TO_REPO}java/pom.xml not found. set <path-to-repo> argument to point to the root of a psoxy checkout. Exiting.${NC}\n"
  exit 1
fi

# 1. Branch/Tag Validation
CURRENT_BRANCH=$(git -C "$PATH_TO_REPO" branch --show-current)
if [ -z "$CURRENT_BRANCH" ]; then
  CURRENT_TAG=$(git -C "$PATH_TO_REPO" describe --tags --exact-match 2>/dev/null)
fi

IS_RC=false
IS_MAIN=false
IS_TAG=false

if [[ -n "$CURRENT_TAG" && "$CURRENT_TAG" =~ ^v ]]; then
  IS_TAG=true
  printf "Detected release tag: ${BLUE}$CURRENT_TAG${NC}\n"
elif [[ "$CURRENT_BRANCH" == "main" ]]; then
  IS_MAIN=true
  printf "Detected main branch.\n"
elif [[ "$CURRENT_BRANCH" =~ ^rc- ]]; then
  IS_RC=true
  printf "Detected RC branch: ${BLUE}$CURRENT_BRANCH${NC}\n"
else
  printf "${RED}Error: This script must be run from 'main', an 'rc-*' branch, or a 'v*' tag.${NC}\n"
  exit 1
fi

cd "${PATH_TO_REPO}java"

# 2. Determine Version
POM_VERSION=$(mvn help:evaluate -Dexpression=revision -q -DforceStdout)

if [ "$IS_RC" = true ]; then
  BASENAME=${CURRENT_BRANCH#rc-} 
  VERSION_NUM=${BASENAME#v}
  TARGET_VERSION="${VERSION_NUM}-SNAPSHOT"
  
  printf "RC build detected. Using version: ${BLUE}${TARGET_VERSION}${NC}\n"
  
  # 3. Delete existing SNAPSHOT artifacts
  printf "Checking for existing artifacts to clean up...\n"
  
  REPO_FULL_NAME=$(git -C "$PATH_TO_REPO" config --get remote.origin.url | sed -E 's/.*github.com[:\/](.*).git/\1/')
  ORG_NAME=$(echo "$REPO_FULL_NAME" | cut -d'/' -f1)

  # List of relevant group IDs to check
  GROUP_IDS=("co.worklytics.psoxy" "com.avaulta.gateway")
  
  printf "  Fetching packages for org: ${BLUE}${ORG_NAME}${NC}...\n"
  
  PACKAGES_JSON=$(gh api "/orgs/${ORG_NAME}/packages?package_type=maven" 2>/dev/null)
  
  if [ $? -ne 0 ]; then
     printf "${RED}Warning: Failed to list packages. Check 'read:packages' scope.${NC}\n"
     PACKAGES_JSON="[]"
  fi
  
  if command -v jq &> /dev/null; then
      
      for GROUP_ID in "${GROUP_IDS[@]}"; do
        printf "  Scanning for packages starting with: ${BLUE}${GROUP_ID}${NC}...\n"
        
        # Filter packages by group ID
        PACKAGE_NAMES=$(echo "$PACKAGES_JSON" | jq -r ".[] | select(.name | startswith(\"${GROUP_ID}\")) | .name")
        
        for PKG in $PACKAGE_NAMES; do
            printf "    Checking package: ${BLUE}${PKG}${NC} for version ${TARGET_VERSION}...\n"
            
            VERSIONS_JSON=$(gh api "/orgs/${ORG_NAME}/packages/maven/${PKG}/versions" 2>/dev/null)
            
            if [ $? -ne 0 ]; then
               printf "      ${RED}Failed to list versions for ${PKG}.${NC}\n"
               continue
            fi
            
            VERSION_ID=$(echo "$VERSIONS_JSON" | jq -r ".[] | select(.name == \"${TARGET_VERSION}\") | .id")
            
            if [ -n "$VERSION_ID" ] && [ "$VERSION_ID" != "null" ]; then
               printf "      Found version ${TARGET_VERSION} (ID: ${VERSION_ID}). Deleting...\n"
               
               if gh api -X DELETE "/orgs/${ORG_NAME}/packages/maven/${PKG}/versions/${VERSION_ID}" 2>/dev/null; then
                  printf "      ${GREEN}✓ Deleted ${PKG}:${TARGET_VERSION}${NC}\n"
               else    
                  printf "      ${RED}✗ Failed to delete ${PKG}:${TARGET_VERSION}. Likely 403 Forbidden.${NC}\n"
                  printf "        Ensure your token has ${BLUE}delete:packages${NC} scope.\n"
               fi
            else
               # Verbose but useful for debugging
               # printf "      Version ${TARGET_VERSION} not found.\n"
               :
            fi
        done
      done
  else
      printf "${RED}Warning: 'jq' not found. Skipping automated cleanup of old artifacts.${NC}\n"
      printf "  Install 'jq' to enable this feature.\n"
  fi
  
else
  TARGET_VERSION="${POM_VERSION}"
  printf "Standard build. Using version: ${BLUE}${TARGET_VERSION}${NC}\n"
fi


printf "\nPublishing Maven artifacts to GitHub Packages ...\n"
printf "  (requires GitHub token with ${BLUE}write:packages${NC} permission in ${BLUE}~/.m2/settings.xml${NC})\n"

if mvn clean deploy -Drevision="${TARGET_VERSION}" -DskipTests; then
  printf "${GREEN}✓${NC} Maven artifacts published to GitHub Packages\n"
else
  printf "${RED}✗${NC} Maven deploy failed. You may need to configure authentication in ~/.m2/settings.xml\n"
  printf "  See: https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry\n"
  exit 1
fi