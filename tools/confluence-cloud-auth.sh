#!/bin/bash

# a script to simplify the process of creating oauth app in Confluence Cloud, authorizing it for the
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

printf "${GREEN}This script will guide you through the process of creating an OAuth app in Confluence Cloud, authorizing it for the required scopes, and obtaining authentication credentials that can be used by the proxy connector.${NC}\n"

printf "1. Go to https://developer.atlassian.com/console/myapps/ and click on \"Create\" and choose \"OAuth 2.0 Integration\"\n"
printf "2. Then click \"Authorization\" and \"Add\" on \`OAuth 2.0 (3L0)\`, adding \`http://localhost\` as callback URI. It can be any URL     that matches the URL format and it is required to be populated, but the proxy instance workflow will not use it.\n"
printf "3. Now navigate to \"Permissions\" and click on \"Add\" for \`Jira API\`. Once added, click on \"Configure\".
  Add following scopes as part of \"Granular Scopes\", first clicking on \`Edit Scopes\` and then selecting them:
    - \`read:blogpost:confluence\`
    - \`read:comment:confluence\`
    - \`read:group:confluence\`
    - \`read:space:confluence\`
    - \`read:attachment:confluence\`
    - \`read:page:confluence\`
    - \`read:user:confluence\`
    - \`read:task:confluence\`
    - \`read:content-details:confluence\`
\n"

printf "4. Once Configured, go to \"Settings\" and copy the \"Client Id\" and \"Secret\". You will use these to
  obtain an OAuth \`refresh_token\`.\n"

# Prompt for Jira client ID and secret
printf "Enter your Confluence Client ID: "
read -r CLIENT_ID
printf "Enter your Confluence Client Secret: "
read -r CLIENT_SECRET

# Open authorization URL in user's browser
AUTH_URL="https://auth.atlassian.com/authorize?audience=api.atlassian.com&client_id=${CLIENT_ID}&scope=offline_access%20read%3Atask%3Aconfluence%20read%3Ablogpost%3Aconfluence%20read%3Acomment%3Aconfluence%20read%3Agroup%3Aconfluence%20read%3Aspace%3Aconfluence%20read%3Aattachment%3Aconfluence%20read%3Apage%3Aconfluence%20read%3Auser%3Aconfluence%20read%3Atask%3Aconfluence%20read%3Acontent-details%3Aconfluence%20&redirect_uri=http%3A%2F%2Flocalhost&state=YOUR_USER_BOUND_VALUE&response_type=code&prompt=consent"
printf "${GREEN}Opening the following URL in your default browser:${NC}\n"
echo $AUTH_URL
open "${AUTH_URL}" || xdg-open "${AUTH_URL}"

# Prompt for authorization code
printf "After accepting access on the Confluence site, paste the full redirect URL here (then press Enter):\n"
if read -e -r REDIRECT_URL; then
  :
else
  read -r REDIRECT_URL
fi
AUTH_CODE=$(echo "$REDIRECT_URL" | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')


# Use the authorization code to request access and refresh tokens
DATA="{\"grant_type\": \"authorization_code\",\"client_id\": \"${CLIENT_ID}\",\"client_secret\": \"${CLIENT_SECRET}\", \"code\": \"${AUTH_CODE}\", \"redirect_uri\": \"http://localhost\"}"
echo $DATA
#exit 1
RESPONSE=$(echo $DATA | curl --silent -X POST --data-binary @- https://auth.atlassian.com/oauth/token -H 'Content-Type: application/json')
# Parse access token and refresh token
echo $RESPONSE
ACCESS_TOKEN=$(echo "${RESPONSE}" | jq -r '.access_token')
REFRESH_TOKEN=$(echo "${RESPONSE}" | jq -r '.refresh_token')

# Use access token to get cloud ID
CLOUD_ID_RESPONSE=$(curl --silent --header "Authorization: Bearer ${ACCESS_TOKEN}" --url 'https://api.atlassian.com/oauth/token/accessible-resources')
CLOUD_ID=$(echo "${CLOUD_ID_RESPONSE}" | jq -r '.[0].id')

# Instructions for user
printf "${GREEN}Use the following values in the secret manager choosen for your host platform:${NC}\n"

printf "${YELLOW}${PREFIX}CONFLUENCE_CLOUD_ACCESS_TOKEN${NC}: ${ACCESS_TOKEN}\n"
printf "${YELLOW}${PREFIX}CONFLUENCE_CLOUD_REFRESH_TOKEN${NC}: ${REFRESH_TOKEN}\n"
printf "${YELLOW}${PREFIX}CONFLUENCE_CLOUD_CLIENT_ID${NC}: ${CLIENT_ID}\n"
printf "${YELLOW}${PREFIX}CONFLUENCE_CLOUD_CLIENT_SECRET${NC}: ${CLIENT_SECRET}\n"

printf "And set the cloud ID (${YELLOW}${CLOUD_ID}${NC}) as the value of ${YELLOW}confluence_cloud_id${NC} in your ${YELLOW}terraform.tfvars${NC} file.\n"

