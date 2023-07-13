#!/bin/bash

# Testing
# from in example directory, such as `infra/examples/aws-msft-365`:
#  ../../../tools/update-bundle.sh ~/code/psoxy/ terraform.tfvars aws
#  ../../../tools/update-bundle.sh ~/code/psoxy/ terraform.tfvars aws s3://my-artifact-bucket/psoxy-project

CLONE_BASE_DIR=$1
TFVARS_FILE=$2
HOST_PLATFORM=$3
BUCKET_PATH=$4

# colors
RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

if [[ ! -d "$1" ]]; then
  printf "${RED}Error: ${CLONE_BASE_DIR} directory does not exist.${NC}\n"
  exit 1
fi

if [[ ! -f "$TFVARS_FILE" ]]; then
  printf "${RED}Error: ${TFVARS_FILE} does not exist.${NC}\n"
  exit 1
fi

RELEASE_VERSION=$(sed -n -e 's/.*<revision>\(.*\)<\/revision>.*/\1/p' "${CLONE_BASE_DIR}java/pom.xml")

printf "Building proxy deployment bundle from code checkout at ${BLUE}${CLONE_BASE_DIR}${NC} for ${BLUE}${HOST_PLATFORM}${NC}; this will take a few minutes ...\n"

${CLONE_BASE_DIR}tools/build.sh -q ${HOST_PLATFORM} ${CLONE_BASE_DIR}java

cp ${CLONE_BASE_DIR}java/impl/${HOST_PLATFORM}/target/psoxy-${HOST_PLATFORM}-${RELEASE_VERSION}.jar .


if [ "$HOST_PLATFORM" == "gcp" ]; then
  zip psoxy-${HOST_PLATFORM}-${RELEASE_VERSION}.zip psoxy-${HOST_PLATFORM}-${RELEASE_VERSION}.jar
  rm psoxy-${HOST_PLATFORM}-${RELEASE_VERSION}.jar
  DEPLOYMENT_BUNDLE="psoxy-${HOST_PLATFORM}-${RELEASE_VERSION}.zip"
elif [ "$HOST_PLATFORM" == "aws" ]; then
  DEPLOYMENT_BUNDLE="psoxy-${HOST_PLATFORM}-${RELEASE_VERSION}.jar"
else
  printf "${RED}Unsupported host platform: ${HOST_PLATFORM}${NC}\n"
  exit 1
fi

# support building bundle and uploading it into an artitfacts bucket
if [ ! -z "$BUCKET_PATH" ]; then
  # if BUCKET_PATH doesn't end with slash, append it
  if [[ ! "$BUCKET_PATH" == */ ]]; then
    BUCKET_PATH="$BUCKET_PATH/"
  fi

  if [ "$HOST_PLATFORM" == "gcp" ]; then
    prefix="gs://"
    copy_cmd=("gsutil" "cp")
    gsutil_version=$(gsutil version 2>&1)

    # If the previous command was not successful (gsutil is not installed)
    if [[ $? -ne 0 ]]; then
        printf "${RED}Error: gsutil is not installed, but it is required to upload bundle. Please install gsutil and re-run this script - or run it without the <bucket-path> argument.${NC}\n"
        exit 1
    fi
  elif [ "$HOST_PLATFORM" == "aws" ]; then
    prefix="s3://"
    copy_cmd=("aws" "s3" "cp")

    s3_version=$(aws --version 2>&1)

    # If the previous command was not successful (gsutil is not installed)
    if [[ $? -ne 0 ]]; then
      printf "${RED}Error: s3 is not installed, but it is required to upload bundle. Please install s3 (included in AWS CLI tools) and re-run this script - or run it without the <bucket-path> argument.${NC}\n"
      exit 1
    fi
  else
    printf "${RED}Unsupported host platform: ${HOST_PLATFORM}${NC}\n"
    exit 1
  fi


  # if bucket path doesn't begin with prefix, prepend it
  if [[ ! "$BUCKET_PATH" == "$prefix"* ]]; then
    BUCKET_PATH="$prefix$BUCKET_PATH"
  fi


  printf "Copying deployment bundle from ${BLUE}${DEPLOYMENT_BUNDLE}${NC} to ${BLUE}${BUCKET_PATH}${NC} ...\n"
  "${copy_cmd[@]}" "${DEPLOYMENT_BUNDLE}" "${BUCKET_PATH}${DEPLOYMENT_BUNDLE}"

  if [[ $? -ne 0 ]]; then
    printf "${RED}Error: Failed to upload deployment bundle to ${BUCKET_PATH}${DEPLOYMENT_BUNDLE}${NC}\n"
    exit 1
  fi

  DEPLOYMENT_BUNDLE="${BUCKET_PATH}${DEPLOYMENT_BUNDLE}"
  printf "Deployment bundle uploaded to ${BLUE}${DEPLOYMENT_BUNDLE}${NC}.\n"
fi


if grep -q '^[[:space:]]*deployment_bundle' "$TFVARS_FILE" ; then
  sed -i .bck "/^[[:space:]]*deployment_bundle.*/c\\
deployment_bundle = \"${DEPLOYMENT_BUNDLE}\"" "$TFVARS_FILE"
  rm ${TFVARS_FILE}.bck
else
  printf "deployment_bundle = \"${DEPLOYMENT_BUNDLE}\"\n\n" >> $TFVARS_FILE
fi

printf "Deployment bundle built: ${BLUE}${DEPLOYMENT_BUNDLE}${NC}.\n"


echo "#!/bin/bash" > ./update-bundle
printf "\n# Use this script rebuild your deployment bundle and update your terraform.tfvars to use the new one\n" >> ./update-bundle
echo "${0} ${@}" >> ./update-bundle
chmod +x ./update-bundle

printf "If you update your proxy version in the future, you can use the ${BLUE}update-bundle${NC} script we've generated in your working directory to update it.\n"

