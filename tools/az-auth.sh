#!/bin/bash

# Copyright 2025 Worklytics, Co.

# Sandbox Azure CLI auth to the current directory to prevent conflicts with other Azure tenants
export AZURE_CONFIG_DIR="${PWD}/.azure"

COLORSCHEME_SH="$(dirname "$0")/set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

printf "Sandboxing Azure CLI authentication state to ${INFO}${AZURE_CONFIG_DIR}${NC}\n"

if ! az -v &> /dev/null ; then
  printf "${ERR}Azure CLI not available.${NC}\n"
  exit 1
fi

TENANT_ID=$1
if [ -f "terraform.tfvars" ] && [ -z "$TENANT_ID" ]; then
  if ! terraform -v &> /dev/null ; then
    printf "${ERR}Terraform not available.${NC}\n"
    exit 1
  fi

  TENANT_ID=$(grep -E "^msft_tenant_id" terraform.tfvars | awk -F'=' '{print $2}' | tr -d '"' | xargs)
  TENANT_ID_PATTERN='^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'

  if [[ ! "$TENANT_ID" =~ $TENANT_ID_PATTERN ]]; then
      printf "${ERR}Error: Failed to parse Microsoft Tenant ID from terraform.tfvars; you can pass as an argument to this tool. Example:${NC}\n"
      printf "Usage: ${INFO}./az-auth.sh <tenant_id>${NC}\n"
      printf "Parsed value was: ${INFO}$TENANT_ID${NC}\n"
      exit 1
  fi
fi

CURRENT_TENANT_ID=$(az account show --query tenantId -o tsv 2>/dev/null)

if [ "$CURRENT_TENANT_ID" != "$TENANT_ID" ]; then
    if [ -z "$CURRENT_TENANT_ID" ]; then
        printf "No current active Azure session.\n"
    else
        printf "Current tenant is ${INFO}${CURRENT_TENANT_ID}${NC}.\r\n"
    fi
    printf "Azure (Microsoft 365) tenant will be forced to ${SUCCESS}${TENANT_ID}${NC}, parsed from your ${INFO}terraform.tfvars${NC}. If you pick user from different tenant, auth will fail.\r\n"
    TENANT_ID_CLAUSE="--tenant ${TENANT_ID}"
    az login --allow-no-subscriptions $TENANT_ID_CLAUSE
else
    printf "Current Azure account is already authenticated against the specified tenant ID ${INFO}${TENANT_ID}${NC}. ${SUCCESS}OK${NC}.\n"
fi

