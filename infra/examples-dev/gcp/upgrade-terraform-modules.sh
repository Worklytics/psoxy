#!/bin/bash

# Check if the psoxy module directory exists
REPO_CLONE_BASE_DIR="../../.././"
if [[ ! -d "$REPO_CLONE_BASE_DIR" ]]; then
  echo "Directory $REPO_CLONE_BASE_DIR does not exist."
  echo "This usually means the Terraform modules haven't been initialized yet."
  echo "Please run ./init again to initialize the Terraform modules first."
  exit 1
fi


"../../.././tools/upgrade-terraform-modules.sh" $1
