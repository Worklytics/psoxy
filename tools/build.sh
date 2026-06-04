#!/bin/bash

# CLI wrapper for building Psoxy deployment JARs outside Terraform.
# Shared logic: tools/lib/build-java-common.sh
# Terraform entry point: infra/modules/psoxy-package/build.sh (different argument order; see TODOs there)

# set to fail on errors
set -e

# shellcheck source=lib/build-java-common.sh
source "$(dirname "$0")/lib/build-java-common.sh"

# colors
COLORSCHEME_SH="$(dirname "$0")/set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

while getopts ":qd" opt; do
  case $opt in
    q)
      QUIET_OPTIONS="-q -Dmaven.test.skip=true"
      ;;
    d)
      DISTRIBUTION_PROFILE="-Pdistribution"
      ;;
    *)
      printf "Usage: build.sh [-qd] <IMPLEMENTATION> <JAVA_SOURCE_ROOT>\n"
      printf "  -q: Skip tests during build\n"
      printf "  -d: build artifact for distribution\n"
      printf "  <IMPLEMENTATION>: 'aws' or 'gcp'\n"
      printf "  <JAVA_SOURCE_ROOT>: Path to the Java source root directory (e.g., 'java/')\n"
      exit 1
      ;;
  esac
done

IMPLEMENTATION=${@:$OPTIND:1}
JAVA_SOURCE_ROOT=${@:$OPTIND+1:1}

# Convert to absolute path if relative
if [[ ! "$JAVA_SOURCE_ROOT" = /* ]]; then
    JAVA_SOURCE_ROOT="$(cd "${JAVA_SOURCE_ROOT}" && pwd)"
fi

psoxy_build_validate_implementation "$IMPLEMENTATION"
psoxy_build_validate_java_source_root "$JAVA_SOURCE_ROOT"

# Ensure trailing slash for path concatenation
if [[ "$JAVA_SOURCE_ROOT" != */ ]]; then
    JAVA_SOURCE_ROOT="${JAVA_SOURCE_ROOT}/"
fi

PARENT_POM="${JAVA_SOURCE_ROOT}pom.xml"

PSOXY_CHECKOUT_ROOT=$(dirname "${JAVA_SOURCE_ROOT%/}")
MAVEN_LOCAL_REPO_SH="$(dirname "$0")/lib/maven-local-repo.sh"
if [ -f "$MAVEN_LOCAL_REPO_SH" ]; then
  # shellcheck source=lib/maven-local-repo.sh
  source "$MAVEN_LOCAL_REPO_SH"
fi

# Use reactor build approach: clean all modules, then build dependencies and target module
# -f specifies the parent pom.xml file
# -pl specifies the projects to build (gateway-core, core, and the implementation)
# -am builds all dependencies of the specified projects (ensures correct build order)

# Clean all modules
mvn ${PSOXY_MAVEN_LOCAL_REPO:+-Dmaven.repo.local="$PSOXY_MAVEN_LOCAL_REPO"} \
    ${PSOXY_SKIP_OPENNLP:+-DskipOpenNlpModelDownload=true} \
    clean $QUIET_OPTIONS -f "${PARENT_POM}"

# Build and install gateway-core and core (dependencies must be installed for impl module)
mvn ${PSOXY_MAVEN_LOCAL_REPO:+-Dmaven.repo.local="$PSOXY_MAVEN_LOCAL_REPO"} \
    ${PSOXY_SKIP_OPENNLP:+-DskipOpenNlpModelDownload=true} \
    install $QUIET_OPTIONS -f "${PARENT_POM}" -pl gateway-core,core -am

# Build the implementation module (package only, not install)
# The reactor will ensure dependencies are available from the previous install step
mvn ${PSOXY_MAVEN_LOCAL_REPO:+-Dmaven.repo.local="$PSOXY_MAVEN_LOCAL_REPO"} \
    ${PSOXY_SKIP_OPENNLP:+-DskipOpenNlpModelDownload=true} \
    package $QUIET_OPTIONS -f "${PARENT_POM}" -pl impl/${IMPLEMENTATION} -am $DISTRIBUTION_PROFILE

DEPLOYMENT_ARTIFACT=$(ls "${JAVA_SOURCE_ROOT}impl/${IMPLEMENTATION}/target/deployment" | grep -E "^psoxy-.*\.jar$" | head -1)

printf "${SUCCESS}Build complete.${NC} Deployment artifact: ${INFO}${DEPLOYMENT_ARTIFACT}${NC}\n"
