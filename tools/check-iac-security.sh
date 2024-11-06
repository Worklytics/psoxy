#!/bin/bash

RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color


printf "This script will use trivy to scan your terraform configuration for compliance with security best practices.\n"

# verify trivy installed
if ! trivy -v &> /dev/null ; then
  printf "${RED}Trivy not installed.${NC} Trivy is required to scan your terraform configuration.\n";
  printf "On macOS, you can install it with ${BLUE}brew install trivy${NC}\n";
  exit 1
fi

# check if .trivyignore exists
if [ ! -f .trivyignore ]; then
  printf ".trivyignore ${BLUE}not found.${NC} Trivy will scan all files in the directory, and check against all its applicable rules.\n";
  printf "See https://aquasecurity.github.io/trivy/v0.20.0/getting-started/configuration/\n";
else
  printf ".trivyignore ${BLUE}found.${NC} Trivy will scan all files in the directory, except those listed in .trivyignore.\n";
  printf "If exceptions are defined in .trivyignore, violations of those rules will not be reported.\n";
  printf "See https://aquasecurity.github.io/trivy/v0.20.0/getting-started/configuration/\n";
fi

if [ -f terraform.tfvars ]; then
  printf "Scanning terraform configuration with trivy...\n";
  trivy config --tf-vars terraform.tfvars .
else
  printf "${RED}No terraform.tfvars file found.${NC} Are you in the right location? Scanning aborted.\n";
fi
