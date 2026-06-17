#!/bin/bash

# Format GitHub auto-generated release notes for psoxy releases.
# Reads release body from stdin; writes formatted body to stdout.
#
# - Prepends empty Features / Fixes / Logistics sections (unless already present)
# - Strips "by @username in" from auto-generated PR lines

set -euo pipefail

body=$(cat)
body=$(printf '%s' "$body" | tr -d '\r')

# Remove GitHub username mentions from auto-generated lines
body=$(printf '%s\n' "$body" | sed -E 's/ by @[^ ]+ in//g')
body="${body%$'\n'}"

if printf '%s' "$body" | grep -qE '^## +Features'; then
  printf '%s' "$body"
  exit 0
fi

sections=$'## Features\n\n## Fixes\n\n## Logistics\n\n'
printf '%s%s' "$sections" "$body"
