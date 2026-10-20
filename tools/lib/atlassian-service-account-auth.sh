#!/bin/bash

# Shared logic for obtaining Atlassian Cloud (Jira/Confluence) authentication credentials for the
# proxy connector, using Atlassian's OAuth 2.0 credentials for service accounts (client_credentials
# grant). See https://support.atlassian.com/user-management/docs/create-oauth-2-0-credential-for-service-accounts/
#
# Sourced by tools/jira-cloud-auth.sh and tools/confluence-cloud-auth.sh.

# Prompts for a service account's OAuth 2.0 Client ID/Secret, exchanges them for an access token,
# looks up the Atlassian Cloud ID, and prints the values to configure for the proxy connector.
#
# Arguments:
#   $1 - product label, for prompts (e.g. "Jira", "Confluence")
#   $2 - secret name component, e.g. "JIRA_CLOUD" or "CONFLUENCE_CLOUD" (used to build
#        <PREFIX>JIRA_CLOUD_CLIENT_ID, etc)
#   $3 - name of the Terraform `connector_settings`/`terraform.tfvars` variable to set with the
#        Cloud ID, e.g. "jira_cloud_id" or "confluence_example_cloud_id"
#   $4 - secret name prefix, defaults to "PSOXY_"
atlassian_service_account_auth() {
  local product_label="$1"
  local secret_name_component="$2"
  local tfvars_variable="$3"
  local prefix="${4:-PSOXY_}"

  # Check if jq is installed
  if ! command -v jq &> /dev/null
  then
      printf "${ERR}jq is not installed.${NC}\n"
      printf "Please install jq to proceed. For example:\n"
      printf "macOS: ${WARN}brew install jq${NC}\n"
      printf "Linux: ${WARN}sudo apt-get install jq${NC} or ${WARN}sudo yum install jq${NC}\n"
      return 1
  fi

  printf "${SUCCESS}This script will obtain an access token and Cloud ID for a ${product_label} Cloud service account OAuth 2.0 credential, and print the values to configure for the proxy connector.${NC}\n"

  # Prompt for client ID and secret
  printf "Enter your ${product_label} service account OAuth 2.0 Client ID: "
  read -r CLIENT_ID
  printf "Enter your ${product_label} service account OAuth 2.0 Client Secret: "
  read -r CLIENT_SECRET

  # Exchange client credentials directly for an access token - no browser/consent step needed
  local response
  response=$(curl --silent --request POST --url 'https://auth.atlassian.com/oauth/token' \
    --header 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'grant_type=client_credentials' \
    --data-urlencode "client_id=${CLIENT_ID}" \
    --data-urlencode "client_secret=${CLIENT_SECRET}")

  echo "$response"
  local access_token
  access_token=$(echo "${response}" | jq -r '.access_token')

  if [ "$access_token" == "null" ] || [ -z "$access_token" ]; then
    printf "${ERR}Failed to obtain an access token; check the response above for error details.${NC}\n"
    return 1
  fi

  # Use access token to get cloud ID
  local cloud_id_response
  cloud_id_response=$(curl --silent --header "Authorization: Bearer ${access_token}" --url 'https://api.atlassian.com/oauth/token/accessible-resources')
  local cloud_id
  cloud_id=$(echo "${cloud_id_response}" | jq -r '.[0].id')

  # Instructions for user
  printf "${SUCCESS}Use the following values in the secret manager choosen for your host platform:${NC}\n"

  printf "${WARN}${prefix}${secret_name_component}_CLIENT_ID${NC}: ${CLIENT_ID}\n"
  printf "${WARN}${prefix}${secret_name_component}_CLIENT_SECRET${NC}: ${CLIENT_SECRET}\n"
  printf "(No ${WARN}${prefix}${secret_name_component}_REFRESH_TOKEN${NC} is needed - this connector no longer uses a refresh token.)\n"

  printf "And set the cloud ID (${WARN}${cloud_id}${NC}) as the value of ${WARN}${tfvars_variable}${NC} in your ${WARN}terraform.tfvars${NC} file.\n"
}
