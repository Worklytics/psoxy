#!/bin/bash
# Thin wrapper around tools/generate-tests.sh.
#
# Customer checkouts get the implementation from the Psoxy module that
# `terraform init` places at .terraform/modules/psoxy/tools/generate-tests.sh.
# Dev examples in this repo use local module sources, so fall back to the
# tools/ directory in the git checkout.

set -euo pipefail

cd "$(cd "$(dirname "$0")" && pwd)"

if [[ -f ".terraform/modules/psoxy/tools/generate-tests.sh" ]]; then
  exec ".terraform/modules/psoxy/tools/generate-tests.sh" "$@"
fi

search_dir="$(pwd)"
while [[ -n "$search_dir" && "$search_dir" != "/" ]]; do
  if [[ -f "${search_dir}/tools/generate-tests.sh" ]]; then
    exec "${search_dir}/tools/generate-tests.sh" "$@"
  fi
  search_dir="$(dirname "$search_dir")"
done

printf 'Could not find generate-tests.sh.\n' >&2
printf 'Run terraform init so the psoxy module is at .terraform/modules/psoxy/, then re-run ./generate-tests.sh.\n' >&2
exit 1
