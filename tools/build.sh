#!/bin/bash

#see ../infra/modules/psoxy-package/build.sh
# this is similar, but outputs errors directly instead of to log file; and doesn'

IMPLEMENTATION=$1 # 'aws' or 'gcp'
JAVA_SOURCE_ROOT=$2

while getopts ":q" opt; do
  case $opt in
    q)
      QUIET_OPTIONS="-q -Dmaven.test.skip=true"
      ;;
  esac
done

QUIET_OPTIONS="-q -Dmaven.test.skip=true"

# set to fail on errors
set -e

mvn $QUIET_OPTIONS -f "${JAVA_SOURCE_ROOT}/pom.xml" clean

mvn package install $QUIET_OPTIONS -f "${JAVA_SOURCE_ROOT}/gateway-core/pom.xml"

mvn package install $QUIET_OPTIONS -f "${JAVA_SOURCE_ROOT}/core/pom.xml"

mvn package $QUIET_OPTIONS -f "${JAVA_SOURCE_ROOT}/impl/${IMPLEMENTATION}/pom.xml"
