#!/bin/bash

# Publish RC bundles (AWS and GCP) via GitHub Actions workflow_dispatch
# Usage: ./publish-rc-bundles-via-gh.sh [ref]
#   ref:  Git ref to trigger workflows on (branch, tag, or any git ref; default: current branch)
#
# This script triggers both AWS and GCP publish workflows and monitors their execution.
# The workflows will read the version from pom.xml automatically.

set -e

# Colors for output
COLORSCHEME_SH="$(dirname "$0")/../set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

# Workflow names
AWS_WORKFLOW="Publish AWS Bundle"
GCP_WORKFLOW="Publish GCP Bundle"

# Check if gh CLI is installed
if ! command -v gh &> /dev/null; then
    echo -e "${ERR}Error: GitHub CLI (gh) is not installed${NC}"
    echo -e "${WARN}Install from: https://cli.github.com/${NC}"
    exit 1
fi

# Check if authenticated
if ! gh auth status &> /dev/null; then
    echo -e "${ERR}Error: Not authenticated with GitHub CLI${NC}"
    echo -e "${WARN}Run 'gh auth login' to authenticate${NC}"
    exit 1
fi

# Get ref (branch, tag, or any git ref; default to current branch)
REF="${1:-$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo '')}"
if [ "$REF" = "HEAD" ]; then
    echo -e "${ERR}Error: Detached HEAD state detected. Please provide a branch, tag, or other git ref.${NC}"
    exit 1
fi
if [ -z "$REF" ]; then
    echo -e "${WARN}Warning: Could not determine current branch, using 'main'${NC}"
    REF="main"
fi

echo -e "${INFO}Target ref: ${SUCCESS}${REF}${NC}"
echo ""

# Function to trigger workflow and get run ID
trigger_workflow() {
    local workflow_name="$1"
    local ref="$2"
    
    echo -e "${INFO}Triggering workflow: ${SUCCESS}${workflow_name}${NC}" >&2
    
    if ! gh workflow run "$workflow_name" --ref "$ref" >&2; then
        echo -e "${ERR}Error: 'gh workflow run' failed for ${workflow_name}${NC}" >&2
        return 1
    fi
    
    # Wait for the workflow to start and get the run ID
    local run_id=""
    local max_attempts=10
    local attempt=0
    
    while [ -z "$run_id" ] || [ "$run_id" = "null" ]; do
        sleep 2
        attempt=$((attempt + 1))
        run_id=$(gh run list --workflow="$workflow_name" --limit 1 --json databaseId,status --jq '.[0].databaseId' 2>/dev/null || echo "")
        
        if [ $attempt -ge $max_attempts ]; then
            echo -e "${ERR}Error: Could not get run ID for workflow after ${max_attempts} attempts${NC}" >&2
            return 1
        fi
    done
    
    echo "$run_id"
}

# Function to watch workflow and return exit code
watch_workflow() {
    local workflow_name="$1"
    local run_id="$2"
    
    echo -e "${INFO}Watching workflow run: ${SUCCESS}${workflow_name}${NC}"
    echo -e "${INFO}Run ID: ${SUCCESS}${run_id}${NC}"
    echo ""
    
    # Watch the workflow run
    if gh run watch "$run_id"; then
        return 0
    else
        return 1
    fi
}

# Function to get workflow status
get_workflow_status() {
    local workflow_name="$1"
    local run_id="$2"
    
    local status=$(gh run view "$run_id" --json conclusion --jq '.conclusion // "unknown"')
    echo "$status"
}

# Function to get workflow URL
get_workflow_url() {
    local run_id="$1"
    local url=$(gh run view "$run_id" --json url --jq '.url')
    echo "$url"
}

# Main execution
echo -e "${INFO}=== Publishing RC Bundles via GitHub Actions ===${NC}"
echo -e "${INFO}Ref: ${SUCCESS}${REF}${NC}"
echo ""

# Trigger AWS workflow
echo -e "${INFO}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${INFO}Step 1/2: AWS Bundle${NC}"
echo -e "${INFO}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
if ! AWS_RUN_ID=$(trigger_workflow "$AWS_WORKFLOW" "$REF") || [ -z "$AWS_RUN_ID" ]; then
    echo -e "${ERR}✗ Failed to trigger AWS workflow${NC}"
    exit 1
fi

AWS_URL=$(get_workflow_url "$AWS_RUN_ID")
echo -e "${INFO}Workflow URL: ${SUCCESS}${AWS_URL}${NC}"
echo ""

# Watch AWS workflow
if watch_workflow "$AWS_WORKFLOW" "$AWS_RUN_ID"; then
    AWS_SUCCESS=true
    echo -e "${SUCCESS}✓ AWS workflow completed successfully${NC}"
else
    AWS_SUCCESS=false
    echo -e "${ERR}✗ AWS workflow failed${NC}"
fi

echo ""
echo ""

# Trigger GCP workflow
echo -e "${INFO}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${INFO}Step 2/2: GCP Bundle${NC}"
echo -e "${INFO}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
if ! GCP_RUN_ID=$(trigger_workflow "$GCP_WORKFLOW" "$REF") || [ -z "$GCP_RUN_ID" ]; then
    echo -e "${ERR}✗ Failed to trigger GCP workflow${NC}"
    exit 1
fi

GCP_URL=$(get_workflow_url "$GCP_RUN_ID")
echo -e "${INFO}Workflow URL: ${SUCCESS}${GCP_URL}${NC}"
echo ""

# Watch GCP workflow
if watch_workflow "$GCP_WORKFLOW" "$GCP_RUN_ID"; then
    GCP_SUCCESS=true
    echo -e "${SUCCESS}✓ GCP workflow completed successfully${NC}"
else
    GCP_SUCCESS=false
    echo -e "${ERR}✗ GCP workflow failed${NC}"
fi

echo ""
echo ""

# Summary
echo -e "${INFO}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${INFO}=== Summary ===${NC}"
echo -e "${INFO}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Get final statuses
AWS_STATUS=$(get_workflow_status "$AWS_WORKFLOW" "$AWS_RUN_ID")
GCP_STATUS=$(get_workflow_status "$GCP_WORKFLOW" "$GCP_RUN_ID")

# AWS status
if [ "$AWS_SUCCESS" = true ]; then
    echo -e "${SUCCESS}✓ AWS Bundle:${NC} SUCCESS"
else
    echo -e "${ERR}✗ AWS Bundle:${NC} FAILED (Status: ${AWS_STATUS})"
fi
echo -e "   URL: ${AWS_URL}"
echo ""

# GCP status
if [ "$GCP_SUCCESS" = true ]; then
    echo -e "${SUCCESS}✓ GCP Bundle:${NC} SUCCESS"
else
    echo -e "${ERR}✗ GCP Bundle:${NC} FAILED (Status: ${GCP_STATUS})"
fi
echo -e "   URL: ${GCP_URL}"
echo ""

# Overall result
if [ "$AWS_SUCCESS" = true ] && [ "$GCP_SUCCESS" = true ]; then
    echo -e "${SUCCESS}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${SUCCESS}✓ All workflows completed successfully!${NC}"
    echo -e "${SUCCESS}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    exit 0
else
    echo -e "${ERR}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${ERR}✗ Some workflows failed${NC}"
    echo -e "${ERR}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    exit 1
fi

