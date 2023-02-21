#!/bin/bash

#see ../infra/modules/psoxy-package/build.sh
# this is similar, but outputs errors directly instead of to log file; and doesn'

IMPLEMENTATION=$1 # 'aws' or 'gcp'
JAVA_SOURCE_ROOT=$2

# set to fail on erors
set -e


#TODO: validate prereqs?? (mvn??)
cd ${JAVA_SOURCE_ROOT}

mvn clean

cd gateway-core
mvn package install

cd ../core
mvn package install

cd ../impl/${IMPLEMENTATION}
mvn package