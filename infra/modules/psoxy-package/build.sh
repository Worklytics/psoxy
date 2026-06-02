#!/bin/bash

# errors halt execution
set -eo pipefail

# psoxy build script to be invoked from Terraform 'external' data resource
# usage ./build.sh -sf /Users/erik/code/psoxy/java aws

PSOXY_BUILD_COMMON="$(cd "$(dirname "$0")" && pwd)/../../../tools/lib/build-java-common.sh"
if [ ! -f "$PSOXY_BUILD_COMMON" ]; then
  printf 'Cannot find build-java-common.sh at %s\n' "$PSOXY_BUILD_COMMON" >&2
  exit 1
fi
# shellcheck source=../../../tools/lib/build-java-common.sh
source "$PSOXY_BUILD_COMMON"

fail() { psoxy_build_fail "$@"; }

psoxy_build_filter_empty_args "$@"

while getopts ":sf" opt; do
  case $opt in
    s)
      OPTIONAL_TEST_SKIP="-Dmaven.test.skip=true"
      ;;
    f)
      FORCE_BUILD=true
      ;;
    *)
      printf "Usage: build.sh [-s] [-f] <JAVA_SOURCE_ROOT> <IMPLEMENTATION>\n" >&2
      printf "  -s: Skip tests during build\n" >&2
      printf "  -f: Force build even if the artifact already exists\n" >&2
      exit 1
      ;;
  esac
done

JAVA_SOURCE_ROOT=${@:$OPTIND:1}
IMPLEMENTATION=${@:$OPTIND+1:1} # expected to be 'aws', 'gcp', etc ...

if [ -z "$JAVA_SOURCE_ROOT" ] || [ -z "$IMPLEMENTATION" ]; then
  fail "Missing required arguments: JAVA_SOURCE_ROOT and IMPLEMENTATION (got JAVA_SOURCE_ROOT='${JAVA_SOURCE_ROOT}', IMPLEMENTATION='${IMPLEMENTATION}')"
fi

psoxy_build_validate_implementation "$IMPLEMENTATION"
psoxy_build_validate_java_source_root "$JAVA_SOURCE_ROOT"

VERSION=$(mvn org.apache.maven.plugins:maven-help-plugin:3.2.0:evaluate -Dexpression=project.version -q -DforceStdout -f "${JAVA_SOURCE_ROOT}/pom.xml")
ARTIFACT_FILE_NAME="psoxy-${IMPLEMENTATION}-${VERSION}.jar"
PATH_TO_DEPLOYMENT_JAR=$(psoxy_build_terraform_jar_path "$JAVA_SOURCE_ROOT" "$IMPLEMENTATION" "$VERSION")

run_mvn() {
  if ! mvn "$@" >> "${LOG_FILE}" 2>&1; then
    printf 'Maven build failed. See %s\n' "${LOG_FILE}" >&2
    if [ -f "${LOG_FILE}" ]; then
      printf 'Last lines of build log:\n' >&2
      tail -n 20 "${LOG_FILE}" >&2
    fi
    exit 1
  fi
}

# build JAR if deployment does not already exist, or anything passed for $FORCE_BUILD
if [ ! -f "$PATH_TO_DEPLOYMENT_JAR" ] || [ -n "$FORCE_BUILD" ]; then
  TERRAFORM_CONFIG_PATH=$(pwd)
  LOG_FILE=/tmp/psoxy-package.$(date +%Y%m%d'T'%H%M%S).log

  ln -sf "${LOG_FILE}" "${TERRAFORM_CONFIG_PATH}/last-build.log"

  run_mvn clean $OPTIONAL_TEST_SKIP -DskipOpenNlpModelDownload=true -f "${JAVA_SOURCE_ROOT}/pom.xml"

  run_mvn package install $OPTIONAL_TEST_SKIP -DskipOpenNlpModelDownload=true -f "${JAVA_SOURCE_ROOT}/gateway-core/pom.xml"

  run_mvn package install $OPTIONAL_TEST_SKIP -DskipOpenNlpModelDownload=true -f "${JAVA_SOURCE_ROOT}/core/pom.xml"

  run_mvn package $OPTIONAL_TEST_SKIP -DskipOpenNlpModelDownload=true -f "${JAVA_SOURCE_ROOT}/impl/${IMPLEMENTATION}/pom.xml"
fi

if [ ! -f "$PATH_TO_DEPLOYMENT_JAR" ]; then
  fail "Deployment artifact not found at ${PATH_TO_DEPLOYMENT_JAR} after build. Check last-build.log in your Terraform working directory."
fi

psoxy_build_compute_jar_hash "$PATH_TO_DEPLOYMENT_JAR"

# output back to Terraform (forces Terraform to be dependent on output)
printf "{\n"
printf "\t\"path_to_deployment_jar\": \"${PATH_TO_DEPLOYMENT_JAR}\",\n"
printf "\t\"filename\":\"${ARTIFACT_FILE_NAME}\",\n"
printf "\t\"version\": \"${VERSION}\",\n"
printf "\t\"deployment_package_hash\": \"${JAR_HASH}\"\n"
printf "}\n"
