#!/bin/bash

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <new_base_branch>"
    exit 1
fi

NEW_BASE_BRANCH=$1

PR_IDS=$(gh pr list --state "open" --base main --json number --jq '.[].number')

for PR_ID in $PR_IDS
do
    gh pr edit $PR_ID --base $NEW_BASE_BRANCH
done