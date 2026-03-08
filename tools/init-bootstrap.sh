#!/bin/bash

# colors - see http://stackoverflow.com/questions/5947742/how-to-change-the-output-color-of-echo-in-linux
COLORSCHEME_SH="$(dirname "$0")/set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi
BLUE='\33[0;34m'

TF_CONFIG_ROOT=`pwd`

if [ ! -f terraform.tfvars ]; then
  TFVARS_FILE="${TF_CONFIG_ROOT}/terraform.tfvars"

  cp "${TF_CONFIG_ROOT}/terraform.tfvars.example" "${TFVARS_FILE}"

  # give user some feedback
  printf "Init'd example terraform config. Please open ${INFO}terraform.tfvars${NC} and customize it to your needs.\n"
  printf "Review ${INFO}variables.tf${NC} for descriptions of each variable.\n\n"
else
  printf "${ERR}Nothing to initialize. File terraform.tfvars already exists!${NC}\n\n"
fi
