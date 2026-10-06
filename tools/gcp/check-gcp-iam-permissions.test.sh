#!/bin/bash
# Fixture tests for tools/gcp/check-gcp-iam-permissions.sh.
# These do not call gcloud or terraform.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CHECK="${ROOT}/tools/gcp/check-gcp-iam-permissions.sh"
WORK="$(mktemp -d)"

cleanup() {
  rm -rf "$WORK"
}
trap cleanup EXIT

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

printf 'compute.instances.get\ncompute.instances.list\n' > "${WORK}/catalog.txt"

printf 'compute.instances.get\n' > "${WORK}/known.txt"
if ! "$CHECK" --permissions-file "${WORK}/known.txt" --catalog-file "${WORK}/catalog.txt" > "${WORK}/ok.out"; then
  fail "known permission was rejected"
fi
grep -q 'Checked 1 GCP permission IDs' "${WORK}/ok.out" || fail "success message missing"

printf 'required_gcp_permissions_to_host\tnot.a.real.permission\n' > "${WORK}/unknown.txt"
set +e
"$CHECK" --permissions-file "${WORK}/unknown.txt" --catalog-file "${WORK}/catalog.txt" > "${WORK}/bad.out" 2> "${WORK}/bad.err"
status=$?
set -e
[[ "$status" -eq 1 ]] || fail "unknown permission exited ${status}, expected 1"
grep -q 'not.a.real.permission' "${WORK}/bad.err" || fail "missing permission was not named"
grep -q 'required_gcp_permissions_to_host' "${WORK}/bad.err" || fail "output name was not named"

printf 'ok\n'
