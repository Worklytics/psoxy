#!/bin/bash

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

if [ -f terraform.tfvars ] ; then
  if ! terraform -v &> /dev/null ; then
    printf "${RED}Terraform not available.${NC}\n"
    exit 1
  fi

  TENANT_ID=`echo "var.msft_tenant_id" | terraform console | tr -d '\"'`
  TENANT_ID_CLAUSE="--tenant ${TENANT_ID}"
  printf "Azure (Microsoft 365) tenant will be forced to ${GREEN}${TENANT_ID}${NC}, parsed from your ${BLUE}terraform.tfvars${NC}. If you pick user from different tenant, auth will fail.\r\n"
fi

az login --allow-no-subscriptions $TENANT_ID_CLAUSE
