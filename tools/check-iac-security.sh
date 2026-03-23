#!/bin/bash

COLORSCHEME_SH="$(dirname "$0")/set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

printf "This script will use trivy to scan your terraform configuration for compliance with security best practices.\n"

# verify trivy installed
if ! trivy -v &> /dev/null ; then
  printf "${ERR}Trivy not installed.${NC} Trivy is required to scan your terraform configuration.\n";
  printf "On macOS, you can install it with ${INFO}brew install trivy${NC}\n";
  exit 1
fi

# check if .trivyignore exists
if [ ! -f .trivyignore ]; then
  printf ".trivyignore ${INFO}not found.${NC} Trivy will scan all files in the directory, and check against all its applicable rules.\n";
  printf "See https://aquasecurity.github.io/trivy/v0.20.0/getting-started/configuration/\n";
else
  printf ".trivyignore ${INFO}found.${NC} Trivy will scan all files in the directory, except those listed in .trivyignore.\n";
  printf "If exceptions are defined in .trivyignore, violations of those rules will not be reported.\n";
  printf "See https://aquasecurity.github.io/trivy/v0.20.0/getting-started/configuration/\n";
fi

if [ -f terraform.tfvars ]; then
  printf "Scanning terraform configuration with trivy...\n";
  trivy config --tf-vars terraform.tfvars .
else
  printf "${ERR}No terraform.tfvars file found.${NC} Are you in the right location? Scanning aborted.\n";
fi
