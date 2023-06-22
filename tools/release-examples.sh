#!/bin/bash

# Usage:
# ./release-examples.sh <path-to-example> <path-to-aws-repo>
# ./release-examples.sh ../infra/examples-dev/aws-all /Users/erik/psoxy-example-aws
# ./release-examples.sh ../infra/examples-dev/gcp /Users/erik/psoxy-example-gcp

DEV_EXAMPLE_PATH=$1
EXAMPLE_TEMPLATE_REPO=$2

FILES_TO_COPY=("main.tf" "variables.tf" "google-workspace.tf" "google-workspace-variables.tf" "msft-365.tf" "msft-365-variables.tf")

for file in "${FILES_TO_COPY[@]}"
do
  cp -f ${DEV_EXAMPLE_PATH}/${file} ${EXAMPLE_TEMPLATE_REPO}/${file}
done
