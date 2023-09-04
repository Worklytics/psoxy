#!/bin/bash

#color constants
BLUE='\e[0;34m'
NC='\e[0m'

#parameters
PROJECT_ID=$1
FUNCTION_NAME=$2
OBSERVATION_COUNT=${3:-10}

if [ -z "$PROJECT_ID" ]; then
  echo "PROJECT_ID is required"
  echo "Usage: ./tools/profile/gcp.sh <project-id> <function-name> <observation-count>"
  exit 1
fi

if [ -z "$FUNCTION_NAME" ]; then
  echo "FUNCTION_NAME is required"
  echo "Usage: ./tools/profile/gcp.sh <project-id> <function-name> <observation-count>"
  exit 1
fi

# Define the query for fetching logs
QUERY="resource.type=\"cloud_function\" AND resource.labels.function_name=\"${FUNCTION_NAME}\" AND severity>=DEFAULT AND \"function execution took\""


printf "Average execution time (ms) for most recent $OBSERVATION_COUNT executions of ${BLUE}${FUNCTION_NAME}${NC}, discarding outliers:\n"

# Fetch and print the logs using gcloud
# takes last 10 entries; sorts them to remove least/greatest as outliers, averages other 8
gcloud logging read "$QUERY" --format="json" --project=$PROJECT_ID --limit=$OBSERVATION_COUNT | jq --argjson count $OBSERVATION_COUNT '[.[] | (.textPayload | match("Function execution took (\\d+) ms").captures[0].string | tonumber)] | reverse | .[0:$count] | sort | .[1:$count-2] | add / length'

