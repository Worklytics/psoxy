#!/bin/bash

# Usage ./tools/check-release.sh <current-release> <next-release>

RED='\e[0;31m'
GREEN='\e[0;32m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

CURRENT_RELEASE=$1
NEXT_RELEASE=$2

if [ -z "$CURRENT_RELEASE" ]; then
  printf "Current release version not specified. Exiting.\n"
  exit 1
fi

if [ -z "$NEXT_RELEASE" ]; then
  printf "Next release version not specified. Exiting.\n"
  exit 1
fi

printf "The following files contain references to the current release version and should be updated:\n"

CURRENT_RELEASE_PATTERN=`echo $CURRENT_RELEASE | sed 's/\./\\./g'`

git grep -l "$CURRENT_RELEASE_PATTERN" java/

git grep -l "$CURRENT_RELEASE_PATTERN" infra/

git grep -l "$CURRENT_RELEASE_PATTERN" tools/

