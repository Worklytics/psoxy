#!/bin/bash

#see ../infra/modules/psoxy-package/build.sh
# this is VERY similar, and should be unified - but it's a breaking change bc argument order is different

# set to fail on errors
set -e

# colors
RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

while getopts ":q" opt; do
  case $opt in
    q)
      QUIET_OPTIONS="-q -Dmaven.test.skip=true"
      ;;
    d)
      DISTRIBUTION_PROFILE="-Pdistribution"
      ;;
    *)
      printf "Usage: build.sh [-q] <IMPLEMENTATION> <JAVA_SOURCE_ROOT>\n"
      printf "  -q: Skip tests during build\n"
      exit 1
      ;;
  esac
done

IMPLEMENTATION=${@:$OPTIND:1}
JAVA_SOURCE_ROOT=${@:$OPTIND+1:1}

if [[ "$IMPLEMENTATION" != "aws" && "$IMPLEMENTATION" != "gcp" ]]; then
    printf "${RED}Error: HOST_PLATFORM value '${IMPLEMENTATION}' must be 'aws' or 'gcp'.${NC}\n"
    printf "Usage: build.sh [-q] <IMPLEMENTATION> <JAVA_SOURCE_ROOT>\n"
    exit 1
fi

if [[ -z "$JAVA_SOURCE_ROOT" ]]; then
    printf "${RED}Error: JAVA_SOURCE_ROOT value is required.${NC}\n"
    printf "Usage: build.sh [-q] <IMPLEMENTATION> <JAVA_SOURCE_ROOT>\n"
    exit 1
fi

if [[ "$JAVA_SOURCE_ROOT" != */ ]]; then
    JAVA_SOURCE_ROOT="${JAVA_SOURCE_ROOT}/"
fi

mvn clean $QUIET_OPTIONS -f "${JAVA_SOURCE_ROOT}pom.xml"

mvn package install $QUIET_OPTIONS -f "${JAVA_SOURCE_ROOT}gateway-core/pom.xml"

mvn package install $QUIET_OPTIONS -f "${JAVA_SOURCE_ROOT}core/pom.xml"

mvn package $QUIET_OPTIONS -f "${JAVA_SOURCE_ROOT}impl/${IMPLEMENTATION}/pom.xml" $DISTRIBUTION_PROFILE

DEPLOYMENT_ARTIFACT=$(ls "${JAVA_SOURCE_ROOT}impl/${IMPLEMENTATION}/target/deployment" | grep -E "^psoxy-.*\.jar$" | head -1)

printf "${GREEN}Build complete.${NC} Deployment artifact: ${BLUE}${DEPLOYMENT_ARTIFACT}${NC}\n"
