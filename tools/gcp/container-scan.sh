#!/bin/bash

# NOTE: this does NOT work with gen2 java cloud run functions, bc they use distroless base images without detectable OS

# NOTE: requires that you've enabled the Container Analysis API for the project
# https://console.developers.google.com/apis/library/containeranalysis.googleapis.com

RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

USAGE=$(printf "Usage: $0 <project_id> <instance_name>\n Example: $0 psoxy-dev-erik psoxy-dev-erik-gcal")

PROJECT_ID=$1
if [ -z "$PROJECT_ID" ]; then
  printf "${RED}Project ID not provided. Exiting.${NC}\n"
  printf "$USAGE\n"
  exit 1
fi

INSTANCE_NAME=$2
if [ -z "$INSTANCE_NAME" ]; then
  printf "${RED}Instance name not provided. Exiting.${NC}\n"
  printf "$USAGE\n"
  exit 1
fi

REGION=us-central1

PROJECT_ID_EXTRA_DASHES=$(echo $PROJECT_ID | sed 's/-/--/g')
REGION_EXTRA_DASHES=$(echo $REGION | sed 's/-/--/g')
INSTANCE_NAME_EXTRA_DASHES=$(echo $INSTANCE_NAME | sed 's/-/--/g')

# Cloud Run artifact names follow pattern: PROJECT__REGION__INSTANCE
ARTIFACT_NAME="${PROJECT_ID_EXTRA_DASHES}__${REGION_EXTRA_DASHES}__${INSTANCE_NAME_EXTRA_DASHES}"

printf "Looking up container image for ${BLUE}${INSTANCE_NAME}${NC}...\n"

VERSION=$(gcloud artifacts docker images list ${REGION}-docker.pkg.dev/${PROJECT_ID}/gcf-artifacts/${ARTIFACT_NAME} --include-tags --format=json 2>/dev/null | jq -r 'sort_by(.createTime)[-1]?.version // empty')

if [ -z "$VERSION" ]; then
  printf "${RED}No container image found for ${INSTANCE_NAME}. Exiting.${NC}\n"
  printf "Make sure the Cloud Run service exists and has been deployed at least once.\n"
  printf "Expected artifact name: ${BLUE}${ARTIFACT_NAME}${NC}\n"
  exit 1
fi

printf "Found image version: ${BLUE}${VERSION}${NC}\n"
printf "Initiating container scan...\n"
printf "Results will be available in the GCP Console: ${BLUE}https://console.cloud.google.com/artifacts/docker/$PROJECT_ID/$REGION/gcf-artifacts/$ARTIFACT_NAME?project=$PROJECT_ID${NC}\n\n"

# Note: Cloud Run images may use minimal base images that don't support OS detection
# The --additional-package-types flag has been removed as it's now the default
set +e
gcloud artifacts docker images scan ${REGION}-docker.pkg.dev/${PROJECT_ID}/gcf-artifacts/${ARTIFACT_NAME}@$VERSION --remote --project=$PROJECT_ID --async
SCAN_EXIT_CODE=$?
set -e

if [ $SCAN_EXIT_CODE -ne 0 ]; then
  printf "\n${RED}Container scan failed.${NC}\n"
  printf "This may happen with minimal base images (distroless/scratch) used by Cloud Run.\n"
  printf "For vulnerability information, check the Cloud Console or try:\n"
  printf "  ${BLUE}gcloud artifacts docker images describe ${REGION}-docker.pkg.dev/${PROJECT_ID}/gcf-artifacts/${ARTIFACT_NAME}@${VERSION}${NC}\n"
  exit $SCAN_EXIT_CODE
fi

printf "${GREEN}Container scan initiated successfully.${NC}\n"
