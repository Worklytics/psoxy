#!/bin/bash

# errors halt execution
set -e

# psoxy build script to be invoked from Terraform 'external' data resource
# usage ./build.sh /Users/erik/code/psoxy/java aws true
JAVA_SOURCE_ROOT=$1
IMPLEMENTATION=$2 # expected to be 'aws', 'gcp', etc ...
FORCE_BUILD=$3

VERSION=$(mvn org.apache.maven.plugins:maven-help-plugin:3.2.0:evaluate -Dexpression=project.version -q -DforceStdout -f ${JAVA_SOURCE_ROOT}/pom.xml)
ARTIFACT_FILE_NAME="psoxy-${IMPLEMENTATION}-${VERSION}.jar"
PATH_TO_DEPLOYMENT_JAR="${JAVA_SOURCE_ROOT}/impl/${IMPLEMENTATION}/target/${ARTIFACT_FILE_NAME}"

# force creation of location where we
# mkdir -p ${JAVA_SOURCE_ROOT}/target/impl/${IMPLEMENTATION}

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
printf "{\n"
printf "\t\"path_to_deployment_jar\": \"${PATH_TO_DEPLOYMENT_JAR}\",\n"
printf "\t\"filename\":\"${ARTIFACT_FILE_NAME},\n"
printf "\t\"version\": \"${VERSION}\"\n"
printf "}\n"
