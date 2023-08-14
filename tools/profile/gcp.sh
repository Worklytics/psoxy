#!/bin/bash

PROJECT_ID=$1
FUNCTION_NAME=$2

if [ -z "$PROJECT_ID" ]; then
  echo "PROJECT_ID is required"
  echo "Usage: ./tools/profile/gcp.sh <project-id> <function-name>"
  exit 1
fi

if [ -z "$FUNCTION_NAME" ]; then
  echo "FUNCTION_NAME is required"
  echo "Usage: ./tools/profile/gcp.sh <project-id> <function-name>"
  exit 1
fi

# Define the query for fetching logs
QUERY="resource.type=\"cloud_function\" AND resource.labels.function_name=\"${FUNCTION_NAME}\" AND severity>=DEFAULT AND \"function execution took\""


# Fetch and print the logs using gcloud
gcloud logging read "$QUERY" --format="json" --project=$PROJECT_ID --limit=10 | jq '[.[] | (.textPayload | match("Function execution took (\\d+) ms").captures[0].string | tonumber)] | reverse | .[0:5] | sort | .[1:4] | add / length'

