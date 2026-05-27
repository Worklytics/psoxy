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
COLORSCHEME_SH="$(dirname "$0")/set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

if [[ -z "$CLONE_BASE_DIR" ]]; then
  printf "${ERR}Error: Missing required argument: CLONE_BASE_DIR${NC}\n"
  printf "Usage: update-bundle.sh <CLONE_BASE_DIR> <TFVARS_FILE> <HOST_PLATFORM> [BUCKET_PATH]\n"
  exit 1
fi

if [[ ! -d "$CLONE_BASE_DIR" ]]; then
  printf "${ERR}Error: ${CLONE_BASE_DIR} directory does not exist.${NC}\n"
  exit 1
fi

if [[ -z "$TFVARS_FILE" ]]; then
  printf "${ERR}Error: Missing required argument: TFVARS_FILE${NC}\n"
  printf "Usage: update-bundle.sh <CLONE_BASE_DIR> <TFVARS_FILE> <HOST_PLATFORM> [BUCKET_PATH]\n"
  exit 1
fi

if [[ ! -f "$TFVARS_FILE" ]]; then
  printf "${ERR}Error: ${TFVARS_FILE} does not exist.${NC}\n"
  exit 1
fi

if [[ "$HOST_PLATFORM" != "aws" && "$HOST_PLATFORM" != "gcp" ]]; then
    echo "Error: HOST_PLATFORM value '${HOST_PLATFORM}' must be 'aws' or 'gcp'."
    exit 1
fi

RELEASE_VERSION=$(sed -n -e 's/.*<revision>\(.*\)<\/revision>.*/\1/p' "${CLONE_BASE_DIR}java/pom.xml")

printf "Building proxy deployment bundle from code checkout at ${INFO}${CLONE_BASE_DIR}${NC} for ${INFO}${HOST_PLATFORM}${NC}; this will take a few minutes ...\n"

${CLONE_BASE_DIR}tools/build.sh -q ${HOST_PLATFORM} ${CLONE_BASE_DIR}java

cp ${CLONE_BASE_DIR}java/impl/${HOST_PLATFORM}/target/psoxy-${HOST_PLATFORM}-${RELEASE_VERSION}.jar .

if [ "$HOST_PLATFORM" == "gcp" ]; then
  zip psoxy-${HOST_PLATFORM}-${RELEASE_VERSION}.zip psoxy-${HOST_PLATFORM}-${RELEASE_VERSION}.jar
  rm psoxy-${HOST_PLATFORM}-${RELEASE_VERSION}.jar
  DEPLOYMENT_BUNDLE="psoxy-${HOST_PLATFORM}-${RELEASE_VERSION}.zip"
  BUCKET_PATH_EXAMPLE="gs://my-artifact-bucket/"
elif [ "$HOST_PLATFORM" == "aws" ]; then
  DEPLOYMENT_BUNDLE="psoxy-${HOST_PLATFORM}-${RELEASE_VERSION}.jar"
  BUCKET_PATH_EXAMPLE="s3://my-artifact-bucket/"
else
  printf "${ERR}Unsupported host platform: ${HOST_PLATFORM}${NC}\n"
  exit 1
fi

# support building bundle and uploading it into an artifacts bucket

if [ -z "$BUCKET_PATH" ]; then
  echo "If you want to upload deployment bundle to a remote storage location, enter the desired bucket url:"
  printf "  example: ${INFO}${BUCKET_PATH_EXAMPLE}${NC}\n"
  echo "  leave blank to skip uploading bundle"
  read -p "Enter the bucket url: (or leave blank for none) " BUCKET_PATH
fi

if [ ! -z "$BUCKET_PATH" ]; then
  # if BUCKET_PATH doesn't end with slash, append it
  if [[ ! "$BUCKET_PATH" == */ ]]; then
    BUCKET_PATH="$BUCKET_PATH/"
  fi

  if [ "$HOST_PLATFORM" == "gcp" ]; then
    prefix="gs://"
    copy_cmd=("gcloud" "storage" "cp")

    if ! command -v gcloud &> /dev/null; then
        printf "${ERR}Error: gcloud is not installed, but it is required to upload bundle. Please install the Google Cloud SDK and re-run this script - or run it without the <bucket-path> argument.${NC}\n"
        exit 1
    fi
  elif [ "$HOST_PLATFORM" == "aws" ]; then
    prefix="s3://"
    copy_cmd=("aws" "s3" "cp")

    s3_version=$(aws --version 2>&1)

    # If the previous command was not successful (AWS CLI is not installed)
    if [[ $? -ne 0 ]]; then
      printf "${ERR}Error: s3 is not installed, but it is required to upload bundle. Please install s3 (included in AWS CLI tools) and re-run this script - or run it without the <bucket-path> argument.${NC}\n"
      exit 1
    fi
  else
    printf "${ERR}Unsupported host platform: ${HOST_PLATFORM}${NC}\n"
    exit 1
  fi

  # if bucket path doesn't begin with prefix, prepend it
  if [[ ! "$BUCKET_PATH" == "$prefix"* ]]; then
    BUCKET_PATH="$prefix$BUCKET_PATH"
  fi

  # Calculate SHA256 of the bundle
  if command -v sha256sum &> /dev/null; then
      SHA256_HASH=$(sha256sum "$DEPLOYMENT_BUNDLE" | cut -d' ' -f1)
  elif command -v shasum &> /dev/null; then
      SHA256_HASH=$(shasum -a 256 "$DEPLOYMENT_BUNDLE" | cut -d' ' -f1)
  else
      SHA256_HASH=$(openssl dgst -sha256 "$DEPLOYMENT_BUNDLE" | cut -d' ' -f2)
  fi

  printf "Copying deployment bundle from ${INFO}${DEPLOYMENT_BUNDLE}${NC} to ${INFO}${BUCKET_PATH}${NC} ...\n"
  
  local upload_status=0
  if [ "$HOST_PLATFORM" == "gcp" ]; then
    gcloud storage cp "${DEPLOYMENT_BUNDLE}" "${BUCKET_PATH}${DEPLOYMENT_BUNDLE}" \
      --update-custom-metadata="sha256=${SHA256_HASH}" || upload_status=$?
  elif [ "$HOST_PLATFORM" == "aws" ]; then
    aws s3 cp "${DEPLOYMENT_BUNDLE}" "${BUCKET_PATH}${DEPLOYMENT_BUNDLE}" --metadata "sha256=${SHA256_HASH}" || upload_status=$?
  fi

  if [[ $upload_status -ne 0 ]]; then
    printf "${ERR}Error: Failed to upload deployment bundle to ${BUCKET_PATH}${DEPLOYMENT_BUNDLE}${NC}\n"
    exit 1
  fi

  DEPLOYMENT_BUNDLE="${BUCKET_PATH}${DEPLOYMENT_BUNDLE}"
  printf "Deployment bundle uploaded to ${INFO}${DEPLOYMENT_BUNDLE}${NC}.\n"
fi

if grep -q '^[[:space:]]*deployment_bundle' "$TFVARS_FILE" ; then
  sed -i .bck "/^[[:space:]]*deployment_bundle.*/c\\
deployment_bundle = \"${DEPLOYMENT_BUNDLE}\"" "$TFVARS_FILE"
  rm ${TFVARS_FILE}.bck
else
  printf "deployment_bundle = \"${DEPLOYMENT_BUNDLE}\"\n\n" >> $TFVARS_FILE
fi

printf "Deployment bundle built: ${INFO}${DEPLOYMENT_BUNDLE}${NC}.\n"

echo "#!/bin/bash" > ./update-bundle
printf "\n# Use this script rebuild your deployment bundle and update your terraform.tfvars to use the new one\n" >> ./update-bundle
echo "${0} ${@}" >> ./update-bundle
chmod +x ./update-bundle

printf "If you update your proxy version in the future, you can use the ${INFO}update-bundle${NC} script we've generated in your working directory to update it.\n"

