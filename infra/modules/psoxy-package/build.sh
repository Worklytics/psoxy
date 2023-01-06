#!/bin/bash

# errors halt execution
set -e
# psoxy build script to be invoked from Terraform 'external' data resource
# NOTE:
JAVA_SOURCE_ROOT=$1
IMPLEMENTATION=$2 # expected to be 'aws', 'gcp', etc ...
PATH_TO_DEPLOYMENT_JAR=$3

TERRAFORM_CONFIG_PATH=`pwd`
LOG_FILE=/tmp/psoxy-package.`date +%Y%m%d'T'%H%M%S`.log

ln -sf ${LOG_FILE} ${TERRAFORM_CONFIG_PATH}/last-build.log

cd ${JAVA_SOURCE_ROOT}/gateway-core
mvn package install > ${LOG_FILE} 2>&1

cd ${JAVA_SOURCE_ROOT}/core
mvn package install > ${LOG_FILE} 2>&1

cd ${JAVA_SOURCE_ROOT}/impl/${IMPLEMENTATION}
mvn package >> ${LOG_FILE} 2>&1

# output back to Terraform
OUTPUT_JSON="{\"path_to_deployment_jar\": \"${PATH_TO_DEPLOYMENT_JAR}\"}"
echo "$OUTPUT_JSON"
