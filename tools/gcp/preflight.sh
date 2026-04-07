#!/bin/bash

COLORSCHEME_SH="$(dirname "$0")/../set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

# if NO terraform.tfvars, exit
if [ ! -f "terraform.tfvars" ]; then
    printf "${ERR}No terraform.tfvars found.${NC}\n"
    exit 1
fi

if [ -f "google-workspace.tf" ]; then
  printf "${INFO}google-workspace.tf${NC} found. (Suggests you're using Google Workspace as a data source) \n"

  GOOGLE_WORKSPACE_GCP_PROJECT_ID=$(grep -E "^google_workspace_gcp_project_id" terraform.tfvars | awk -F'=' '{print $2}' | tr -d '"' | xargs)
fi

GCP_PROJECT_ID=$(grep -E "^gcp_project_id" terraform.tfvars | awk -F'=' '{print $2}' | tr -d '"' | xargs)

# if either GCP_PROJECT_ID or GOOGLE_WORKSPACE_GCP_PROJECT_ID exists
if [[ -z "$GCP_PROJECT_ID" ]] && [[ -z "$GOOGLE_WORKSPACE_GCP_PROJECT_ID" ]]; then
    printf "No GCP project id references; not validating gcloud setup found in terraform.tfvars.\n"
    exit 0
fi

if ! command -v gcloud &> /dev/null
then
    printf "${ERR}gcloud is not installed.${NC}\n"
    exit 1
fi

GCLOUD_ACCOUNT=$(gcloud config get-value account)

if [[ -z "$GCLOUD_ACCOUNT" ]]
then
    printf "${ERR}gcloud is not authenticated.${NC}\n"
    exit 1
fi

printf "gcloud is authenticated as ${INFO}${GCLOUD_ACCOUNT}${NC}.\n"

# q: is there a good way to validate GCLOUD_ACCOUNTS perms on GCP_PROJECT_ID, without requiring READ of IAM?  (perms to do that ARE not proxy prereqs)
