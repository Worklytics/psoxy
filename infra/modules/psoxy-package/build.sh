#!/bin/bash
# psoxy build script to be invoked from Terraform 'external' data resource
# NOTE:
cd $1
cd core
mvn package install > psoxy-package.log 2>&1

cd ../impl/$2
mvn package >> psoxy-package.log 2>&1

# output back to Terraform
OUTPUT_JSON="{\"path_to_deployment_jar\": \"$3\"}"
echo "$OUTPUT_JSON"
