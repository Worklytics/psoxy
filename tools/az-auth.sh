#!/bin/bash

# Copyright 2025 Worklytics, Co.

RED='\e[0;31m'
GREEN='\e[0;32m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

if ! terraform -v &> /dev/null ; then
  printf "${RED}Terraform not available.${NC}\n"
  exit 1
fi

if ! az -v &> /dev/null ; then
  printf "${RED}Azure CLI not available.${NC}\n"
  exit 1
fi

TENANT_ID=$1
if [ -f "terraform.tfvars" ] && [ -z "$TENANT_ID" ]; then
  if ! terraform -v &> /dev/null ; then
    printf "${RED}Terraform not available.${NC}\n"
    exit 1
  fi

  TENANT_ID=`echo "var.msft_tenant_id" | terraform console | tr -d '\"'`

  TENANT_ID_PATTERN='^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'

  if [[ ! "$TENANT_ID" =~ $TENANT_ID_PATTERN ]]; then
      printf "${RED}Error: Failed to parse Microsoft Tenant ID from terraform.tfvars; you can pass as an argument to this tool. Example:${NC}\n"
      printf "Usage: ${BLUE}./az-auth.sh <tenant_id>${NC}\n"
      printf "Parsed value was: ${BLUE}$TENANT_ID${NC}\n"
      exit 1
  fi
fi

CURRENT_TENANT_ID=$(az account show --query tenantId -o tsv)

if [ "$CURRENT_TENANT_ID" != "$TENANT_ID" ]; then
    printf "Current tenant is ${BLUE}${CURRENT_TENANT_ID}${NC}.\r\n"
    printf "Azure (Microsoft 365) tenant will be forced to ${GREEN}${TENANT_ID}${NC}, parsed from your ${BLUE}terraform.tfvars${NC}. If you pick user from different tenant, auth will fail.\r\n"
    TENANT_ID_CLAUSE="--tenant ${TENANT_ID}"
    az login --allow-no-subscriptions $TENANT_ID_CLAUSE
else
    printf "Current Azure account is already authenticated against the specified tenant ID ${BLUE}${TENANT_ID}${NC}. ${GREEN}OK${NC}.\n"
fi

