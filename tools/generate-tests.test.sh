#!/bin/bash
# Behavioral tests for tools/generate-tests.sh.
#
# Stubs `terraform output -json` so this does not need cloud credentials or a real apply.
#
# Usage (from the repo root or anywhere):
#   ./tools/generate-tests.test.sh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT="${ROOT}/tools/generate-tests.sh"
AWS_WRAPPER="${ROOT}/infra/examples-dev/aws/generate-tests.sh"
GCP_WRAPPER="${ROOT}/infra/examples-dev/gcp/generate-tests.sh"

if ! command -v jq >/dev/null 2>&1; then
  printf 'jq is required to run these tests\n' >&2
  exit 1
fi

ORIG_PATH="${PATH}"
WORK=""
FAKE_BIN=""

cleanup() {
  if [[ "${KEEP_TMP:-}" != 1 ]]; then
    rm -rf "${WORK:-}" "${FAKE_BIN:-}"
  fi
}
trap cleanup EXIT

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  if [[ -n "${WORK:-}" ]]; then
    printf 'workspace: %s\n' "$WORK" >&2
  fi
  exit 1
}

assert_file() {
  local path="$1"
  local expected="$2"
  local expected_file
  [[ -f "$path" ]] || fail "missing file ${path}"
  expected_file="$(mktemp)"
  printf '%s' "$expected" > "$expected_file"
  if ! cmp -s "$path" "$expected_file"; then
    printf 'FAIL: content mismatch for %s\n' "$path" >&2
    printf '--- got ---\n' >&2
    cat -v "$path" >&2
    printf '\n--- expected ---\n' >&2
    cat -v "$expected_file" >&2
    printf '\n' >&2
    rm -f "$expected_file"
    exit 1
  fi
  rm -f "$expected_file"
}

reset_work() {
  rm -rf "$WORK"
  WORK="$(mktemp -d)"
  printf '# fixture\n' > "${WORK}/main.tf"
}

setup_fake_terraform() {
  FAKE_BIN="$(mktemp -d)"
  cat > "${FAKE_BIN}/terraform" <<'EOF'
#!/bin/bash
if [[ "${1:-}" == "output" && "${2:-}" == "-json" ]]; then
  cat "$TF_OUTPUT_FIXTURE"
  exit 0
fi
printf 'unexpected terraform args: %s\n' "$*" >&2
exit 1
EOF
  chmod +x "${FAKE_BIN}/terraform"
  export PATH="${FAKE_BIN}:${ORIG_PATH}"
  export TF_OUTPUT_FIXTURE="${WORK}/fixture.json"
}

run_script() {
  (cd "$WORK" && "$SCRIPT" "$@" </dev/null)
}

write_fixture() {
  cat > "${WORK}/fixture.json"
}

test_writes_test_script_files() {
  reset_work
  setup_fake_terraform
  write_fixture <<'EOF'
{
  "test_script_files": {
    "sensitive": false,
    "type": ["map", "string"],
    "value": {
      "test-gcal.sh": "#!/bin/bash\necho gcal\n",
      "test-all.sh": "#!/bin/bash\necho all\n",
      "empty.sh": ""
    }
  }
}
EOF
  run_script >/dev/null
  assert_file "${WORK}/test-gcal.sh" $'#!/bin/bash\necho gcal\n'
  assert_file "${WORK}/test-all.sh" $'#!/bin/bash\necho all\n'
  [[ -x "${WORK}/test-gcal.sh" ]] || fail "test-gcal.sh should be executable"
  [[ ! -e "${WORK}/empty.sh" ]] || fail "empty script content was written"
  printf 'ok writes test_script_files\n'
}

test_always_overwrites_existing() {
  reset_work
  setup_fake_terraform
  write_fixture <<'EOF'
{
  "test_script_files": {
    "sensitive": false,
    "type": ["map", "string"],
    "value": { "test-gcal.sh": "from output" }
  }
}
EOF
  printf 'local\n' > "${WORK}/test-gcal.sh"
  run_script >/dev/null
  assert_file "${WORK}/test-gcal.sh" $'from output\n'
  printf 'ok always overwrites existing\n'
}

test_unsafe_path_does_not_escape_workdir() {
  reset_work
  setup_fake_terraform
  write_fixture <<'EOF'
{
  "test_script_files": {
    "sensitive": false,
    "type": ["map", "string"],
    "value": {
      "test-ok.sh": "fine",
      "skip/me.sh": "nope"
    }
  }
}
EOF
  set +e
  run_script >/dev/null 2>&1
  local rc=$?
  set -e
  [[ "$rc" -eq 1 ]] || fail "unsafe path should exit 1, got ${rc}"
  assert_file "${WORK}/test-ok.sh" $'fine\n'
  [[ ! -e "${WORK}/skip/me.sh" && ! -d "${WORK}/skip" ]] || fail "unsafe path was created"
  printf 'ok rejects unsafe paths\n'
}

test_no_test_content_exits_cleanly() {
  reset_work
  setup_fake_terraform
  write_fixture <<'EOF'
{ "todo_files": { "sensitive": false, "type": ["map", "string"], "value": { "TODO 1.md": "x" } } }
EOF
  local out
  out="$(run_script)"
  [[ "$out" == *"No test script content found"* ]] || fail "missing empty-output message: ${out}"
  printf 'ok empty outputs\n'
}

test_refuses_to_run_outside_terraform_root() {
  local empty
  empty="$(mktemp -d)"
  set +e
  (cd "$empty" && "$SCRIPT" </dev/null >/dev/null 2>&1)
  local rc=$?
  set -e
  rm -rf "$empty"
  [[ "$rc" -ne 0 ]] || fail "script should fail when no *.tf files are present"
  printf 'ok requires a terraform root\n'
}

test_wrappers_match_and_find_implementation() {
  diff -q "$AWS_WRAPPER" "$GCP_WRAPPER" >/dev/null || fail "aws and gcp generate-tests.sh wrappers differ"

  local tree stub_out
  tree="$(mktemp -d)"
  mkdir -p "${tree}/example/.terraform/modules/psoxy/tools" "${tree}/tools"
  cp "$AWS_WRAPPER" "${tree}/example/generate-tests.sh"
  chmod +x "${tree}/example/generate-tests.sh"
  printf '#!/bin/bash\nprintf "MODULE_COPY\n"\n' > "${tree}/example/.terraform/modules/psoxy/tools/generate-tests.sh"
  printf '#!/bin/bash\nprintf "WALK_UP\n"\nexit 9\n' > "${tree}/tools/generate-tests.sh"
  chmod +x "${tree}/example/.terraform/modules/psoxy/tools/generate-tests.sh" "${tree}/tools/generate-tests.sh"

  stub_out="$("${tree}/example/generate-tests.sh" </dev/null)"
  [[ "$stub_out" == *"MODULE_COPY"* ]] || fail "wrapper did not use .terraform module copy: ${stub_out}"

  rm -rf "${tree}/example/.terraform"
  stub_out="$("${tree}/example/generate-tests.sh" </dev/null)" || true
  [[ "$stub_out" == *"WALK_UP"* ]] || fail "wrapper did not walk up to tools/generate-tests.sh: ${stub_out}"
  rm -rf "$tree"
  printf 'ok wrappers\n'
}

test_writes_test_script_files
test_always_overwrites_existing
test_unsafe_path_does_not_escape_workdir
test_no_test_content_exits_cleanly
test_refuses_to_run_outside_terraform_root
test_wrappers_match_and_find_implementation

printf 'all generate-tests tests passed\n'
