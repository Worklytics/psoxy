#!/bin/bash

RED='\033[0;31m'
NC='\033[0m' # No Color

EXAMPLE_TO_COPY_FROM=$1
EXAMPLE_TEMPLATE_REPO=$2

if [ -z "$EXAMPLE_TO_COPY_FROM" ]; then
  printf "${RED}Path to example is required.${NC}\n"
  printf "Usage: ./example-copy.sh <path-to-example> <path-to-example-repo>\n"
  exit 1
fi

if [ -z "$EXAMPLE_TEMPLATE_REPO" ]; then
  printf "${RED}Path to example repo is required.${NC}\n"
  printf "Usage: ./example-copy.sh <path-to-example> <path-to-example-repo>\n"
  exit 1
fi


cd "$EXAMPLE_TO_COPY_FROM"
FILES_TO_COPY=( *.tf )

for file in "${FILES_TO_COPY[@]}"
do
  if [ -f ${EXAMPLE_TO_COPY_FROM}/${file} ]; then
     echo "copying ${EXAMPLE_TO_COPY_FROM}/${file} to ${EXAMPLE_TEMPLATE_REPO}${file}"
     cp -f ${EXAMPLE_TO_COPY_FROM}/${file} ${EXAMPLE_TEMPLATE_REPO}${file}

     # uncomment Terraform module remotes
     sed -i .bck 's/^\(.*\)# source = "git::\(.*\)"/\1source = "git::\2"/' "${EXAMPLE_TEMPLATE_REPO}${file}"

     # remove references to local modules
     sed -i .bck '/source = "..\/..\/modules\/[^"]*"/d' "${EXAMPLE_TEMPLATE_REPO}${file}"
  fi
done

rm ${EXAMPLE_TEMPLATE_REPO}/*.bck

cp -f ${PATH_TO_REPO}tools/init-example.sh ${EXAMPLE_TEMPLATE_REPO}init
chmod +x ${EXAMPLE_TEMPLATE_REPO}init


cp -f ${PATH_TO_REPO}tools/check-prereqs.sh ${EXAMPLE_TEMPLATE_REPO}check-prereqs
chmod +x ${EXAMPLE_TEMPLATE_REPO}check-prereqs
