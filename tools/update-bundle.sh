#!/bin/bash

PSOXY_BASE_DIR=$1
TFVARS_FILE=$2
HOST_PLATFORM=$3

# colors
RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

RELEASE_VERSION=$(sed -n -e 's/.*<revision>\(.*\)<\/revision>.*/\1/p' "${PSOXY_BASE_DIR}java/pom.xml")

printf "Building psoxy bundle from code checkout at ${BLUE}${PSOXY_BASE_DIR}${NC} for ${BLUE}${HOST_PLATFORM}${NC}; this will take a few minutes ...\n"

${PSOXY_BASE_DIR}tools/build.sh ${HOST_PLATFORM} ${PSOXY_BASE_DIR}java -q

cp ${PSOXY_BASE_DIR}java/impl/${HOST_PLATFORM}/target/psoxy-${HOST_PLATFORM}-${RELEASE_VERSION}.jar .


if [ "$HOST_PLATFORM" == "gcp" ]; then
  zip psoxy-${HOST_PLATFORM}-${RELEASE_VERSION}.zip psoxy-${HOST_PLATFORM}-${RELEASE_VERSION}.jar
  rm psoxy-${HOST_PLATFORM}-${RELEASE_VERSION}.jar
  DEPLOYMENT_BUNDLE="psoxy-${HOST_PLATFORM}-${RELEASE_VERSION}.zip"
else
  DEPLOYMENT_BUNDLE="psoxy-${HOST_PLATFORM}-${RELEASE_VERSION}.jar"
fi

if grep -q '^[[:space:]]*deployment_bundle' "$TFVARS_FILE" ; then
  sed -i .bck "/^[[:space:]]*deployment_bundle.*/c\\
deployment_bundle = \"${DEPLOYMENT_BUNDLE}\"\\n" "$TFVARS_FILE"
  rm *.bck
else
  echo "deployment_bundle = \"${DEPLOYMENT_BUNDLE}\"" >> $TFVARS_FILE
fi

printf "Deployment bundle built: ${BLUE}${DEPLOYMENT_BUNDLE}${NC}. You should commit this to your repo.\n"

echo "#!/bin/bash" > ./update-bundle
echo "${0} ${@}" >> ./update-bundle
chmod +x update-bundle

printf "If you update your proxy version in the future, you can use the ${BLUE}update-bundle${NC} script we've generated in your working directory to update it.\n"

