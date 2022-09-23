#!/bin/bash

# errors halt execution
set -e
# psoxy build script to be invoked from Terraform 'external' data resource
# NOTE:
TERRAFORM_CONFIG_PATH=`pwd`
LOG_FILE=${TERRAFORM_CONFIG_PATH}/psoxy-package.`date +%Y%m%d'T'%H%M%S`.log

FOLDERS=("gateway-core" "core" "impl/$2")

touch ${LOG_FILE}

cd $1
for index in "${!FOLDERS[@]}"
do
  # will only re-gen package if contents of the folder have changed
  MD5_FILE=${TERRAFORM_CONFIG_PATH}/module${index}.md5
  MD5_FILE_NEW=${TERRAFORM_CONFIG_PATH}/module${index}-new.md5
  touch ${MD5_FILE}
  # use tar as also checks changes in attributes, not just contents
  echo "tar -cf - "${FOLDERS[index]}" | md5sum --tag"  >> ${LOG_FILE}
  tar -cf - "$1/${FOLDERS[index]}" | md5sum --tag > ${MD5_FILE_NEW}
  if diff ${MD5_FILE} ${MD5_FILE_NEW} > /dev/null
  then
      echo "No differences in ${FOLDERS[index]}" >> ${LOG_FILE}
  else
      echo "MD5 differences in ${FOLDERS[index]}" >> ${LOG_FILE}
      cd $1/"${FOLDERS[index]}"
      mvn clean package install >> ${LOG_FILE} 2>&1
      mv ${MD5_FILE_NEW} ${MD5_FILE}
  fi
  rm -f ${MD5_FILE_NEW}
done

# output back to Terraform
OUTPUT_JSON="{\"path_to_deployment_jar\": \"$3\"}"
echo "$OUTPUT_JSON"
