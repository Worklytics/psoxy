#!/bin/bash

# Append or replace a marked block in GitHub release notes.
# Usage: amend-github-release-notes.sh <tag> <identifier> <block-file>
#
# <identifier> must appear twice in the block (start/end markers), e.g. <!-- aws-artifacts-info -->
# Requires: gh, GH_TOKEN

set -euo pipefail

TAG_NAME="${1:?tag required}"
IDENTIFIER="${2:?identifier required}"
BLOCK_FILE="${3:?block file required}"

if [ ! -f "$BLOCK_FILE" ]; then
  echo "Block file not found: $BLOCK_FILE" >&2
  exit 1
fi

# Remove an existing marked block (identifier ... identifier) from release notes.
remove_marked_block() {
  local identifier="$1"
  local body="$2"
  local rest after before

  rest="${body#*"$identifier"}"
  if [ "$rest" = "$body" ]; then
    printf '%s' "$body"
    return
  fi

  after="${rest#*"$identifier"}"
  if [ "$after" = "$rest" ]; then
    printf '%s' "$body"
    return
  fi

  before="${body:0:$((${#body} - ${#rest} - ${#identifier}))}"
  printf '%s%s' "$before" "$after"
}

release_updated=false
for _ in {1..5}; do
  echo "Fetching latest release body..."
  current_body=$(gh release view "$TAG_NAME" --json body -q '.body')

  if [[ "$current_body" == *"$IDENTIFIER"* ]]; then
    echo "Release already contains artifact references for this identifier. Replacing block..."
    new_body=$(remove_marked_block "$IDENTIFIER" "$current_body")
  else
    new_body="$current_body"
  fi

  new_body=$(printf "%s\n%s" "$new_body" "$(cat "$BLOCK_FILE")")

  echo "Updating release body on GitHub..."
  if gh release edit "$TAG_NAME" --notes "$new_body"; then
    echo "Successfully updated release body!"
    release_updated=true
    break
  fi

  sleep_time=$((RANDOM % 5 + 2))
  echo "Failed to update release, retrying in ${sleep_time} seconds..."
  sleep "$sleep_time"
done

if [ "$release_updated" != true ]; then
  echo "::warning::Failed to update GitHub release notes after 5 attempts. The bundle was published successfully; update the release notes manually if needed."
  exit 0
fi
