#!/bin/bash


# NOTE: requires that you've enabled the Container Analysis API for the project
# https://console.developers.google.com/apis/library/containeranalysis.googleapis.com

RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

USAGE=$(printf "Usage: $0 <project_id> <instance_name>\n Example: $0 psoxy-dev-erik psoxy-dev-erik-gcal")
å
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

INSTANCE_NAME_EXTRA_DASHES=$(echo $INSTANCE_NAME | sed 's/-/--/g')

VERSION=$(gcloud artifacts docker images list ${REGION}-docker.pkg.dev/${PROJECT_ID}/gcf-artifacts/${INSTANCE_NAME_EXTRA_DASHES} --format=json | jq -r 'max_by(.createTime) | .version')

printf "Initiating container scan for ${BLUE}${INSTANCE_NAME}:${VERSION}${NC}\n"
printf "Results will be available in the GCP Console: ${BLUE}https://console.cloud.google.com/artifacts/docker/$PROJECT_ID/$REGION/gcf-artifacts/$INSTANCE_NAME_EXTRA_DASHES?project=$PROJECT_ID${NC}\n"

gcloud artifacts docker images scan ${REGION}-docker.pkg.dev/${PROJECT_ID}/gcf-artifacts/${INSTANCE_NAME_EXTRA_DASHES}@$VERSION --additional-package-types=MAVEN --remote --project=$PROJECT_ID --async

printf "${GREEN}Container scan initiated.${NC}\n"
