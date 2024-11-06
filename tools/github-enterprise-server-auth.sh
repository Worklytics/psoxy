#!/bin/bash

# a script to simplify the process of creating GitHub app in GitHub Enterprise Server, authorizing it for the
# the required scopes, and obtaining authentication credentials that can be used by the proxy
# connector

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

printf "${GREEN}This script will guide you through the process of creating an GitHub app in GitHub Enterprise Server, authorizing it for the required permissions, and obtaining authentication credentials that can be used by the proxy connector.${NC}\n"

printf "Enter the host where your GitHub Enteprise Server is: "
read -r GITHUB_ENTERPRISE_SERVER_HOST

printf "1. Go to https://${GITHUB_ENTERPRISE_SERVER_HOST}/settings/apps and click on \"New GitHub app\"\n"
printf "2. Put a name for your app and then put \`http://localhost\` as \"HomePage URL\" and for \"Callback URL\"\n"
printf "3. Ensure that \"Expire user authorization tokens\" is marked and Webhooks are disabled\n"
printf "4. Set following with following permissions with **Read Only**:
    - Repository:
      - Contents: for reading commits and comments
      - Issues: for listing issues, comments, assignees, etc.
      - Metadata: for listing repositories and branches
      - Pull requests: for listing pull requests, reviews, comments and commits
    - Organization
      - Administration: for listing events from audit log
      - Members: for listing teams and their members
\n"
printf "5. Once created, go to the app settings and copy the \"Client ID\"\n"
printf "Enter your Client ID: "
read -r CLIENT_ID

printf "6. And now generate a new \"Client Secret\":\n"
printf "Enter your Client Secret: "
read -r CLIENT_SECRET

# Open authorization URL in user's browser
AUTH_URL="https://${GITHUB_ENTERPRISE_SERVER_HOST}/login/oauth/authorize?client_id=${CLIENT_ID}"
printf "${GREEN}Opening the following URL in your default browser:${NC}\n"
echo $AUTH_URL
open "${AUTH_URL}" || xdg-open "${AUTH_URL}"

# Prompt for authorization code
printf "After accepting access on the GitHub application, paste the authorization code from the URL here: "
read -r AUTH_CODE

# Use the authorization code to request access and refresh tokens
#exit 1
RESPONSE=$(curl --silent --location --request POST "https://${GITHUB_ENTERPRISE_SERVER_HOST}/login/oauth/access_token?client_id=${CLIENT_ID}&client_secret=${CLIENT_SECRET}&code=${AUTH_CODE}" --header 'Content-Type: application/json' --header 'Accept: application/json')
# Parse access token and refresh token
echo $RESPONSE
ACCESS_TOKEN=$(echo "${RESPONSE}" | jq -r '.access_token')
REFRESH_TOKEN=$(echo "${RESPONSE}" | jq -r '.refresh_token')

printf "${YELLOW}${PREFIX}GITHUB_ENTERPRISE_SERVER_REFRESH_TOKEN${NC}: ${REFRESH_TOKEN}\n"
printf "${YELLOW}${PREFIX}GITHUB_ENTERPRISE_SERVER_CLIENT_ID${NC}: ${CLIENT_ID}\n"
printf "${YELLOW}${PREFIX}GITHUB_ENTERPRISE_SERVER_CLIENT_SECRET${NC}: ${CLIENT_SECRET}\n"