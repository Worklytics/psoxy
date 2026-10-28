#!/bin/bash

# Shared logic to look up the Atlassian Cloud ID for a Jira/Confluence Cloud site.
#
# The Cloud ID is NOT required to authenticate or run the jira-cloud/confluence-cloud connectors
# (those use a service account OAuth 2.0 credential - just CLIENT_ID/CLIENT_SECRET). It's only used
# to populate `connector_settings` in `terraform.tfvars` so generated example/test API call URLs
# target the right site.
#
# Sourced by tools/jira-cloud-id.sh and tools/confluence-cloud-id.sh.

# Prompts for an Atlassian site URL, looks up its Cloud ID (via the unauthenticated
# `_edge/tenant_info` endpoint - no credentials needed), and prints the Terraform variable to set.
#
# Arguments:
#   $1 - name of the Terraform `connector_settings`/`terraform.tfvars` variable to set with the
#        Cloud ID, e.g. "jira_cloud_id" or "confluence_example_cloud_id"
atlassian_lookup_cloud_id() {
  local tfvars_variable="$1"

  if ! command -v jq &> /dev/null
  then
      printf "${ERR}jq is not installed.${NC}\n"
      printf "Please install jq to proceed. For example:\n"
      printf "macOS: ${WARN}brew install jq${NC}\n"
      printf "Linux: ${WARN}sudo apt-get install jq${NC} or ${WARN}sudo yum install jq${NC}\n"
      return 1
  fi

  printf "Enter your Atlassian site URL (e.g. https://your-site.atlassian.net): "
  read -r SITE_URL
  SITE_URL="${SITE_URL%/}"

  local response
  response=$(curl --silent "${SITE_URL}/_edge/tenant_info")

  local cloud_id
  cloud_id=$(echo "${response}" | jq -r '.cloudId // empty')

  if [ -z "$cloud_id" ]; then
    printf "${ERR}Could not determine Cloud ID; response was:${NC}\n%s\n" "$response"
    return 1
  fi

  printf "${SUCCESS}Cloud ID:${NC} ${WARN}${cloud_id}${NC}\n"
  printf "Set this as the value of ${WARN}${tfvars_variable}${NC} in your ${WARN}terraform.tfvars${NC} file's ${WARN}connector_settings${NC} (e.g. ${WARN}connector_settings = { ${tfvars_variable} = \"${cloud_id}\" }${NC}), to generate example/test API call URLs for a valid instance.\n"
}
