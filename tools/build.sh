#!/bin/bash

#see ../infra/modules/psoxy-package/build.sh
# this is similar, but outputs errors directly instead of to log file; and doesn'

IMPLEMENTATION=$1 # 'aws' or 'gcp'
JAVA_SOURCE_ROOT=$2

# set to fail on errors
set -e

#TODO: validate prereqs?? (mvn??)

mvn -f ${JAVA_SOURCE_ROOT}/pom.xml clean

mvn package install -f ${JAVA_SOURCE_ROOT}/gateway-core/pom.xml

mvn package install -f ${JAVA_SOURCE_ROOT}/core/pom.xml

mvn package -f ${JAVA_SOURCE_ROOT}/impl/${IMPLEMENTATION}/pom.xml
