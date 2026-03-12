#!/bin/bash

# Copyright 2025 Worklytics, Co.

COLORSCHEME_SH="$(dirname "$0")/../set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

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

printf "Testing assumption of role ${INFO}${AWS_ROLE_ARN}${NC} ${INFO}${PROFILE_OPTION}${NC} ... "

aws sts assume-role $PROFILE_OPTION --role-arn "$AWS_ROLE_ARN" --role-session-name PreflightCheckSession > /dev/null

if [ $? -ne 0 ]; then
  printf "${ERR}Failed to assume role. Exiting.${NC}\n"
  exit 1
else
  printf "${SUCCESS}Success!${NC}\n"
fi

