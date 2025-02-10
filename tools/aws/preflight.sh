#!/bin/bash

# Copyright 2025 Worklytics, Co.

BLUE='\033[0;34m'
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color


OPTSTRING=":p:r:"

while getopts ${OPTSTRING} opt; do
  case ${opt} in
    p)
        AWS_PROFILE=${OPTARG};;
    r)
        AWS_ROLE_ARN=${OPTARG};;
    ?)
      echo "Invalid option: -${OPTARG}."
      exit 1
      ;;
  esac
done

# if AWS_PROFILE is provided
if [ "$AWS_PROFILE" ]; then
    PROFILE_OPTION="--profile ${AWS_PROFILE}"
fi

# if not provided, try to parse from Terraform configuration, assuming our standard naming
# convention
if [ -z "$AWS_ROLE_ARN" ]; then
  AWS_ROLE_ARN=$(echo "local.aws_admin_role_arn"  | terraform console | tr -d '"\n')
fi

printf "Testing assumption of role ${BLUE}${AWS_ROLE_ARN}${NC} ${BLUE}${PROFILE_OPTION}${NC} ... "

aws sts assume-role $PROFILE_OPTION --role-arn "$AWS_ROLE_ARN" --role-session-name PreflightCheckSession > /dev/null

if [ $? -ne 0 ]; then
  printf "${RED}Failed to assume role. Exiting.${NC}\n"
  exit 1
else
  printf "${GREEN}Success!${NC}\n"
fi

