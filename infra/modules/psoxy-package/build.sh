#!/bin/bash
# psoxy build script to be invoked from Terraform 'external' data resource
# NOTE:
TERRAFORM_CONFIG_PATH=`pwd`
LOG_FILE=${TERRAFORM_CONFIG_PATH}/psoxy-package.`date +%Y%m%d'T'%H%M%S`.log
cd $1
cd core
mvn package install > ${LOG_FILE} 2>&1

cd ../impl/$2
mvn package >> ${LOG_FILE} 2>&1

# output back to Terraform
OUTPUT_JSON="{\"path_to_deployment_jar\": \"$3\"}"
echo "$OUTPUT_JSON"
