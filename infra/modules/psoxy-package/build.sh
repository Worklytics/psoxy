#!/bin/bash

# errors halt execution
set -e

# psoxy build script to be invoked from Terraform 'external' data resource
JAVA_SOURCE_ROOT=$1
IMPLEMENTATION=$2 # expected to be 'aws', 'gcp', etc ...
PATH_TO_DEPLOYMENT_JAR=$3
FORCE_BUILD=$4

#  build JAR if deployment does already not exist, or anything passed for $FORCE_BUILD
if [ ! -f $PATH_TO_DEPLOYMENT_JAR ] || [ ! -z "$FORCE_BUILD" ] ; then
  TERRAFORM_CONFIG_PATH=`pwd`
  LOG_FILE=/tmp/psoxy-package.`date +%Y%m%d'T'%H%M%S`.log

  ln -sf ${LOG_FILE} ${TERRAFORM_CONFIG_PATH}/last-build.log

  cd ${JAVA_SOURCE_ROOT}/gateway-core
  mvn package install > ${LOG_FILE} 2>&1

  cd ${JAVA_SOURCE_ROOT}/core
  mvn package install > ${LOG_FILE} 2>&1

  cd ${JAVA_SOURCE_ROOT}/impl/${IMPLEMENTATION}
  mvn package >> ${LOG_FILE} 2>&1
fi

# output back to Terraform (forces Terraform to be dependent on output)
OUTPUT_JSON="{\"path_to_deployment_jar\": \"${PATH_TO_DEPLOYMENT_JAR}\"}"
echo "$OUTPUT_JSON"
