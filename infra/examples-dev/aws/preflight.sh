#!/bin/bash

# script to check if Terraform cloud providers have basic authentication
# configured correctly based on variables.tf and environment variables
set -e

if [ -f "$(dirname "$0")/set-term-colorscheme.sh" ]; then
  source "$(dirname "$0")/set-term-colorscheme.sh"
elif [ -f "$(dirname "$0")/../../tools/set-term-colorscheme.sh" ]; then
  source "$(dirname "$0")/../../tools/set-term-colorscheme.sh"
else
  # Fallbacks
  ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

printf "${INFO}Running preflight checks for Terraform providers ...${NC}\n\n"

# ======== AWS Check ========
if grep -q 'provider "aws"' *.tf 2>/dev/null || grep -q 'aws_assume_role_arn' variables.tf 2>/dev/null; then
  printf "Checking AWS Provider Auth...\n"
  if ! aws sts get-caller-identity &> /dev/null; then
    printf "  ${ERR}AWS CLI is not authenticated.${NC} Run 'aws configure' or 'aws sso login'.\n"
  else
    printf "  ${SUCCESS}AWS CLI is authenticated.${NC}\n"
    # Attempt to extract assumed role from tfvars if it exists
    if [ -f "terraform.tfvars" ]; then
      ASSUME_ROLE=$(grep "aws_assume_role_arn" terraform.tfvars | cut -d "=" -f 2 | tr -d ' "') || true
      if [[ -n "$ASSUME_ROLE" && "$ASSUME_ROLE" != "null" ]]; then
         printf "  Attempting to assume AWS Role: ${CODE}${ASSUME_ROLE}${NC} ...\n"
         if aws sts assume-role --role-arn "$ASSUME_ROLE" --role-session-name "preflight-test" &> /dev/null; then
           printf "  ${SUCCESS}Successfully assumed AWS Role from TFVars.${NC}\n"
         else
           printf "  ${ERR}Failed to assume AWS role: $ASSUME_ROLE.${NC} Check trust policies and permissions.\n"
         fi
      fi
    fi
  fi
  printf "\n"
fi

# ======== GCP Check ========
if grep -q 'provider "google"' *.tf 2>/dev/null || grep -q 'provider "google-workspace"' *.tf 2>/dev/null || grep -q 'provider "google-beta"' *.tf 2>/dev/null; then
  printf "Checking GCP Provider Auth...\n"
  if ! gcloud auth list --filter="status:ACTIVE" --format="value(account)" 2>/dev/null | grep -q '@'; then
    printf "  ${ERR}Google Cloud CLI is not authenticated.${NC} Run 'gcloud auth login' and 'gcloud auth application-default login'.\n"
  else
    printf "  ${SUCCESS}Google Cloud CLI is authenticated.${NC}\n"
    # Attempt to extract impersonated service account if configured
    if [ -f "terraform.tfvars" ]; then
      IMPERSONATE_SA=$(grep "gcp_terraform_sa_account_email" terraform.tfvars | cut -d "=" -f 2 | tr -d ' "') || true
      if [[ -n "$IMPERSONATE_SA" && "$IMPERSONATE_SA" != "null" ]]; then
         printf "  Attempting to impersonate GCP Service Account: ${CODE}${IMPERSONATE_SA}${NC} ...\n"
         # Testing impersonation by getting an access token
         if gcloud auth print-access-token --impersonate-service-account="$IMPERSONATE_SA" &> /dev/null; then
           printf "  ${SUCCESS}Successfully impersonated GCP Service Account via gcloud.${NC}\n"
         else
           printf "  ${ERR}Failed to impersonate Service Account: ${IMPERSONATE_SA}.${NC}\n"
         fi
      fi
    fi
  fi
  printf "\n"
fi

# ======== Azure/AzureAD Check ========
if grep -q 'provider "azuread"' *.tf 2>/dev/null || grep -q 'provider "azurerm"' *.tf 2>/dev/null; then
  printf "Checking Azure Provider Auth...\n"
  if ! az account show &> /dev/null; then
    printf "  ${ERR}Azure CLI is not authenticated.${NC} Run 'az login' or 'az login --allow-no-subscription'.\n"
  else
    printf "  ${SUCCESS}Azure CLI is authenticated.${NC}\n"
    TENANT_ID=$(az account show --query tenantId -o tsv)
    printf "  Current Azure Tenant: ${CODE}${TENANT_ID}${NC}\n"
  fi
  printf "\n"
fi

printf "${SUCCESS}Preflight checks complete.${NC}\n"
