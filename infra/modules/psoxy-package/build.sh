#!/bin/bash

# errors halt execution
set -e

# psoxy build script to be invoked from Terraform 'external' data resource
# usage ./build.sh /Users/erik/code/psoxy/java aws true

while getopts ":sf" opt; do
  case $opt in
    s)
      OPTIONAL_TEST_SKIP="-Dmaven.test.skip=true"
      ;;
    f)
      FORCE_BUILD=true
      ;;
    *)
      printf "Usage: build.sh [-s] [-f] <JAVA_SOURCE_ROOT> <IMPLEMENTATION>\n"
      printf "  -s: Skip tests during build\n"
      printf "  -f: Force build even if the artifact already exists\n"
      exit 1
      ;;
  esac
done

JAVA_SOURCE_ROOT=${@:$OPTIND:1}
IMPLEMENTATION=${@:$OPTIND+1:1} # expected to be 'aws', 'gcp', etc ...

VERSION=$(mvn org.apache.maven.plugins:maven-help-plugin:3.2.0:evaluate -Dexpression=project.version -q -DforceStdout -f "${JAVA_SOURCE_ROOT}/pom.xml")
ARTIFACT_FILE_NAME="psoxy-${IMPLEMENTATION}-${VERSION}.jar"
PATH_TO_DEPLOYMENT_JAR="${JAVA_SOURCE_ROOT}/impl/${IMPLEMENTATION}/target/${ARTIFACT_FILE_NAME}"

# force creation of location where we
# mkdir -p ${JAVA_SOURCE_ROOT}/target/impl/${IMPLEMENTATION}

#  build JAR if deployment does already not exist, or anything passed for $FORCE_BUILD
if [ ! -f $PATH_TO_DEPLOYMENT_JAR ] || [ ! -z "$FORCE_BUILD" ] ; then
  TERRAFORM_CONFIG_PATH=`pwd`
  LOG_FILE=/tmp/psoxy-package.`date +%Y%m%d'T'%H%M%S`.log

  ln -sf ${LOG_FILE} "${TERRAFORM_CONFIG_PATH}/last-build.log"

  mvn clean $OPTIONAL_TEST_SKIP -f "${JAVA_SOURCE_ROOT}/pom.xml" > ${LOG_FILE} 2>&1

  mvn package install $OPTIONAL_TEST_SKIP -f "${JAVA_SOURCE_ROOT}/gateway-core/pom.xml" > ${LOG_FILE} 2>&1

  mvn package install $OPTIONAL_TEST_SKIP -f "${JAVA_SOURCE_ROOT}/core/pom.xml" >> ${LOG_FILE} 2>&1

  mvn package $OPTIONAL_TEST_SKIP -f "${JAVA_SOURCE_ROOT}/impl/${IMPLEMENTATION}/pom.xml" >> ${LOG_FILE} 2>&1
fi

# output back to Terraform (forces Terraform to be dependent on output)
printf "{\n"
printf "\t\"path_to_deployment_jar\": \"${PATH_TO_DEPLOYMENT_JAR}\",\n"
printf "\t\"filename\":\"${ARTIFACT_FILE_NAME}\",\n"
printf "\t\"version\": \"${VERSION}\"\n"
printf "}\n"
