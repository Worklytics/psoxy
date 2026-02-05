#!/bin/bash

# a script to simplify the process of creating oauth app in Gong, authorizing it for the
# the required scopes, and obtaining authentication credentials that can be used by the proxy
# connector (similar to https://github.com/Worklytics/psoxy-oauth-setup-tool)

# Prefer printf over echo for compatibility and formatting
# Use colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

PREFIX="${1:-PSOXY_}"

# Check if jq is installed
if ! command -v jq &> /dev/null
then
    printf "${RED}jq is not installed.${NC}\n"
    printf "Please install jq to proceed. For example:\n"
    printf "macOS: ${YELLOW}brew install jq${NC}\n"
    printf "Linux: ${YELLOW}sudo apt-get install jq${NC} or ${YELLOW}sudo yum install jq${NC}\n"
    exit 1
fi

printf "${GREEN}This script will guide you through the process of creating an OAuth app in Gong, authorizing it for the required scopes, and obtaining authentication credentials that can be used by the proxy connector.${NC}\n"

printf "See official documentation: ${YELLOW}https://help.gong.io/docs/create-an-app-for-gong${NC}\n\n"

printf "1. Log in to your Gong workspace with administrative privileges\n"
printf "2. Navigate to ${YELLOW}Company Settings${NC} → ${YELLOW}Ecosystem${NC} → ${YELLOW}Apps & Integrations${NC} → ${YELLOW}Create an app${NC}\n"
printf "3. Fill in the required information:\n"
printf "   - ${YELLOW}App Name${NC}: (e.g., \"Worklytics Analytics Connector\")\n"
printf "   - ${YELLOW}Description${NC}: (describe the purpose of this integration)\n"
printf "   - ${YELLOW}Redirect URL${NC}: \`http://localhost\`\n"
printf "4. Configure the required ${YELLOW}scopes${NC} for this connector:\n"
printf "   - \`api:stats:user-actions\` - for retrieving user activity statistics\n"
printf "   - \`api:users:read\` - for listing users and their details\n"
printf "5. Once the app is created, copy the ${YELLOW}Client ID${NC} and ${YELLOW}Client Secret${NC}\n\n"

# Prompt for Gong client ID and secret
printf "Enter your Gong Client ID: "
read -r CLIENT_ID
printf "Enter your Gong Client Secret: "
read -r CLIENT_SECRET

# Open authorization URL in user's browser
AUTH_URL="https://app.gong.io/oauth2/authorize?client_id=${CLIENT_ID}&response_type=code&scope=api:stats:user-actions%20api:users:read&redirect_uri=http://localhost&state=YOUR_RANDOM_STATE"
printf "${GREEN}Opening the following URL in your default browser:${NC}\n"
echo $AUTH_URL
open "${AUTH_URL}" || xdg-open "${AUTH_URL}"

# Prompt for authorization code
printf "\nAfter accepting access on the Gong site, paste the full redirect URL here (then press Enter):\n"
if read -e -r REDIRECT_URL; then
  :
else
  read -r REDIRECT_URL
fi
AUTH_CODE=$(echo "$REDIRECT_URL" | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')

if [ -z "$AUTH_CODE" ]; then
    printf "${RED}Failed to extract authorization code from URL. Please check the URL and try again.${NC}\n"
    exit 1
fi

printf "${GREEN}Authorization code obtained: ${AUTH_CODE}${NC}\n"

# Create Base64-encoded credentials for Basic Auth
BASIC_AUTH=$(echo -n "${CLIENT_ID}:${CLIENT_SECRET}" | base64)

# Use the authorization code to request access and refresh tokens
# Gong uses application/x-www-form-urlencoded format with Basic Auth header
printf "${GREEN}Exchanging authorization code for tokens...${NC}\n"
RESPONSE=$(curl --silent -X POST \
  --url 'https://app.gong.io/oauth2/generate-customer-token' \
  --header "Authorization: Basic ${BASIC_AUTH}" \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --data "grant_type=authorization_code&code=${AUTH_CODE}&client_id=${CLIENT_ID}&redirect_uri=http://localhost")

# Parse access token, refresh token, and API base URL
echo $RESPONSE
ACCESS_TOKEN=$(echo "${RESPONSE}" | jq -r '.access_token')
REFRESH_TOKEN=$(echo "${RESPONSE}" | jq -r '.refresh_token')
API_BASE_URL=$(echo "${RESPONSE}" | jq -r '.api_base_url_for_customer')
EXPIRES_IN=$(echo "${RESPONSE}" | jq -r '.expires_in')

if [ "$ACCESS_TOKEN" = "null" ] || [ -z "$ACCESS_TOKEN" ]; then
    printf "${RED}Failed to obtain access token. Response:${NC}\n"
    echo $RESPONSE
    exit 1
fi

# Instructions for user
printf "\n${GREEN}✓ Successfully obtained OAuth tokens!${NC}\n\n"
printf "${GREEN}Use the following values in the secret manager chosen for your host platform:${NC}\n\n"

printf "${YELLOW}${PREFIX}GONG_METRICS_CLIENT_ID${NC}: ${CLIENT_ID}\n"
printf "${YELLOW}${PREFIX}GONG_METRICS_CLIENT_SECRET${NC}: ${CLIENT_SECRET}\n"
printf "${YELLOW}${PREFIX}GONG_METRICS_ACCESS_TOKEN${NC}: ${ACCESS_TOKEN}\n"
printf "${YELLOW}${PREFIX}GONG_METRICS_REFRESH_TOKEN${NC}: ${REFRESH_TOKEN}\n"

printf "\n${GREEN}Additional information:${NC}\n"
printf "Access token expires in: ${YELLOW}${EXPIRES_IN}${NC} seconds ($(expr ${EXPIRES_IN} / 3600) hours)\n"
printf "Customer API Base URL: ${YELLOW}${API_BASE_URL}${NC}\n"

if [ "$API_BASE_URL" != "null" ] && [ -n "$API_BASE_URL" ]; then
    # Extract instance name from API base URL (e.g., "acme" from "https://acme.api.gong.io")
    INSTANCE_NAME=$(echo "$API_BASE_URL" | sed -n 's|https://\([^.]*\)\..*|\1|p')
    if [ -n "$INSTANCE_NAME" ]; then
        printf "\n${YELLOW}IMPORTANT:${NC} Set the following in your ${YELLOW}terraform.tfvars${NC} file:\n"
        printf "${YELLOW}gong_instance_name${NC} = \"${INSTANCE_NAME}\"\n"
    fi
fi

printf "\n${GREEN}Note:${NC} The access token will be automatically refreshed by the proxy using the refresh token.\n"

