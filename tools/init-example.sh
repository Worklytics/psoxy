#!/bin/bash

# colors - see http://stackoverflow.com/questions/5947742/how-to-change-the-output-color-of-echo-in-linux
RED='\033[0;31m'
BLUE='\33[0;34m'
NC='\033[0m' # No Color

if [ -f terraform.tfvars ]; then
    printf "${RED}Nothing to initialize. File terraform.tfvars already exists!${NC}\n"
    exit 1 # error
fi

cp terraform.tfvars.example terraform.tfvars

# append root of checkout automatically
cd ../../..
echo "psoxy_base_dir                = \"$(pwd)\"" >> infra/examples/aws-google-workspace/terraform.tfvars

# give user some feedback
printf "Init'd example terraform config. Please open ${BLUE}terraform.tfvars${NC} and customize it to your needs.\n"
printf "Review ${BLUE}variables.tf${NC} for descriptions of each variable.\n"


