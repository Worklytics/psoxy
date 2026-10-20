#!/bin/bash

# a script to simplify obtaining Confluence Cloud authentication credentials for the proxy connector,
# using Atlassian's OAuth 2.0 credentials for service accounts (client_credentials grant).
# See https://support.atlassian.com/user-management/docs/create-oauth-2-0-credential-for-service-accounts/
#
# Prerequisite: an org admin must have already created a service account (admin.atlassian.com >
# Directory > Service accounts), granted it product access to the Confluence site, and created an
# OAuth 2.0 credential for it with these granular scopes:
#   read:blogpost:confluence, read:comment:confluence, read:group:confluence, read:space:confluence,
#   read:attachment:confluence, read:page:confluence, read:user:confluence, read:task:confluence,
#   read:content-details:confluence

# Prefer printf over echo for compatibility and formatting
# Use colors for output
COLORSCHEME_SH="$(dirname "$0")/set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

# shellcheck source=lib/atlassian-service-account-auth.sh
source "$(dirname "$0")/lib/atlassian-service-account-auth.sh"

PREFIX="${1:-PSOXY_}"

printf "Before continuing, in ${WARN}https://admin.atlassian.com${NC}, an org admin must have:\n"
printf "  1. Created a service account (Directory > Service accounts) with product access to your Confluence site.\n"
printf "  2. Created an OAuth 2.0 credential for that service account (Settings/API keys > OAuth 2.0 credentials), with granular scopes:\n"
printf "     ${CODE}read:blogpost:confluence${NC}, ${CODE}read:comment:confluence${NC}, ${CODE}read:group:confluence${NC}, ${CODE}read:space:confluence${NC},\n"
printf "     ${CODE}read:attachment:confluence${NC}, ${CODE}read:page:confluence${NC}, ${CODE}read:user:confluence${NC}, ${CODE}read:task:confluence${NC},\n"
printf "     ${CODE}read:content-details:confluence${NC}\n\n"

atlassian_service_account_auth "Confluence" "CONFLUENCE_CLOUD" "confluence_example_cloud_id" "${PREFIX}"
