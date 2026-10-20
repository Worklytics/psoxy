#!/bin/bash

# a script to simplify obtaining Jira Cloud authentication credentials for the proxy connector,
# using Atlassian's OAuth 2.0 credentials for service accounts (client_credentials grant).
# See https://support.atlassian.com/user-management/docs/create-oauth-2-0-credential-for-service-accounts/
#
# Prerequisite: an org admin must have already created a service account (admin.atlassian.com >
# Directory > Service accounts), granted it product access to the Jira site, and created an
# OAuth 2.0 credential for it with these scopes:
#   Classic: read:jira-user, read:jira-work
#   Granular: read:group:jira, read:avatar:jira, read:user:jira

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
printf "  1. Created a service account (Directory > Service accounts) with product access to your Jira site.\n"
printf "  2. Created an OAuth 2.0 credential for that service account (Settings/API keys > OAuth 2.0 credentials), with scopes:\n"
printf "     Classic: ${CODE}read:jira-user${NC}, ${CODE}read:jira-work${NC}\n"
printf "     Granular: ${CODE}read:group:jira${NC}, ${CODE}read:avatar:jira${NC}, ${CODE}read:user:jira${NC}\n\n"

atlassian_service_account_auth "Jira" "JIRA_CLOUD" "jira_cloud_id" "${PREFIX}"
