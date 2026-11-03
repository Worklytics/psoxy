#!/bin/bash

# Looks up the Atlassian Cloud ID for your Confluence site.
#
# This is NOT required to authenticate or run the confluence-cloud connector - that uses a service
# account OAuth 2.0 credential (CLIENT_ID/CLIENT_SECRET), set up per
# docs/sources/atlassian/confluence/README.md. The Cloud ID is only used to populate
# `connector_settings.confluence_example_cloud_id` in `terraform.tfvars`, so generated example/test
# API call URLs target your Confluence site.

COLORSCHEME_SH="$(dirname "$0")/set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

# shellcheck source=lib/atlassian-cloud-id.sh
source "$(dirname "$0")/lib/atlassian-cloud-id.sh"

atlassian_lookup_cloud_id "confluence_example_cloud_id"
