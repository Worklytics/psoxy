#!/usr/bin/env bash

set -euo pipefail

# Use semantic colors dynamically based on terminal capability
if [ -t 1 ] && command -v tput >/dev/null 2>&1; then
    ERR=$(tput setaf 1)
    SUCCESS=$(tput setaf 2)
    WARN=$(tput setaf 3)
    INFO=$(tput setaf 4)
    NC=$(tput sgr0)
else
    ERR='\033[0;31m'
    SUCCESS='\033[0;32m'
    WARN='\033[1;33m'
    INFO='\033[0;34m'
    NC='\033[0m'
fi

BASE_URL="http://opennlp.sourceforge.net/models-1.5"
DEST_DIR="java/gateway-core/src/main/resources/opennlp"

# The core English models required for the pipeline
MODELS=(
    "en-sent.bin"
    "en-pos-maxent.bin"
    "en-chunker.bin"
    "en-ner-person.bin"
    "en-ner-location.bin"
    "en-ner-organization.bin"
    "en-ner-date.bin"
)

# Move to the project root
cd "$(dirname "$0")/.."

mkdir -p "$DEST_DIR"

printf "${INFO}Downloading Apache OpenNLP models to %s...${NC}\n" "$DEST_DIR"

for MODEL in "${MODELS[@]}"; do
    TARGET_PATH="$DEST_DIR/$MODEL"
    if [ -s "$TARGET_PATH" ]; then
        printf "${WARN}Skipping %s (already exists and not empty)${NC}\n" "$MODEL"
        continue
    fi
    
    printf "${INFO}Fetching %s...${NC}\n" "$MODEL"
    # Download following redirects. If it fails, remove the empty/partial file.
    if curl -L -f -s -o "$TARGET_PATH" "$BASE_URL/$MODEL"; then
        printf "${SUCCESS}Successfully downloaded %s${NC}\n" "$MODEL"
    else
        printf "${ERR}Failed to download %s${NC}\n" "$MODEL"
        rm -f "$TARGET_PATH"
        exit 1
    fi
done

# The lemmatizer dictionary is not hosted on the standard 1.5 SF mirror, fetching from a reliable GitHub mirror
LEMMATIZER_URL="https://raw.githubusercontent.com/richardwilly98/elasticsearch-opennlp-auto-tagging/master/src/main/resources/models/en-lemmatizer.dict"
LEMMATIZER_TARGET="$DEST_DIR/en-lemmatizer.dict"

if [ ! -s "$LEMMATIZER_TARGET" ]; then
    printf "${INFO}Fetching en-lemmatizer.dict...${NC}\n"
    if curl -L -f -s -o "$LEMMATIZER_TARGET" "$LEMMATIZER_URL"; then
        printf "${SUCCESS}Successfully downloaded en-lemmatizer.dict${NC}\n"
    else
        printf "${ERR}Failed to download en-lemmatizer.dict${NC}\n"
        rm -f "$LEMMATIZER_TARGET"
        exit 1
    fi
else
    printf "${WARN}Skipping en-lemmatizer.dict (already exists and not empty)${NC}\n"
fi

printf "${SUCCESS}All required OpenNLP models downloaded successfully.${NC}\n"
