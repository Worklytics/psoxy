#!/bin/bash
# Behavioral tests for tools/generate-todos.sh.
#
# Stubs `terraform output -json` so this does not need cloud credentials or a real apply.
#
# Usage (from the repo root or anywhere):
#   ./tools/generate-todos.test.sh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT="${ROOT}/tools/generate-todos.sh"
AWS_WRAPPER="${ROOT}/infra/examples-dev/aws/generate-todos.sh"
GCP_WRAPPER="${ROOT}/infra/examples-dev/gcp/generate-todos.sh"

if ! command -v jq >/dev/null 2>&1; then
  printf 'jq is required to run these tests\n' >&2
  exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
  printf 'python3 is required for the overwrite-prompt test\n' >&2
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
  cat "$TODO_FIXTURE"
  exit 0
fi
printf 'unexpected terraform args: %s\n' "$*" >&2
exit 1
EOF
  chmod +x "${FAKE_BIN}/terraform"
  export PATH="${FAKE_BIN}:${ORIG_PATH}"
  export TODO_FIXTURE="${WORK}/fixture.json"
}

run_script() {
  (cd "$WORK" && "$SCRIPT" "$@" </dev/null)
}

write_fixture() {
  cat > "${WORK}/fixture.json"
}

test_writes_todo_files_and_ignores_joined_blobs() {
  reset_work
  setup_fake_terraform
  write_fixture <<'EOF'
{
  "todo_files": {
    "sensitive": false,
    "type": ["map", "string"],
    "value": {
      "TODO 2 - test beta.md": "beta line\nsecond line\n",
      "TODO 1 - setup alpha.md": "alpha body",
      "empty.md": ""
    }
  },
  "todos_1": {
    "sensitive": false,
    "type": "string",
    "value": "joined blob that must not be written"
  }
}
EOF
  run_script --yes >/dev/null
  assert_file "${WORK}/TODO 1 - setup alpha.md" $'alpha body\n'
  assert_file "${WORK}/TODO 2 - test beta.md" $'beta line\nsecond line\n'
  [[ ! -e "${WORK}/empty.md" ]] || fail "empty TODO content was written"
  [[ ! -e "${WORK}/todos_1.md" ]] || fail "joined todos_1 was written even though todo_files exists"
  printf 'ok writes todo_files\n'
}

test_noninteractive_keeps_existing_unless_yes() {
  reset_work
  setup_fake_terraform
  write_fixture <<'EOF'
{
  "todo_files": {
    "sensitive": false,
    "type": ["map", "string"],
    "value": { "TODO 1 - setup alpha.md": "from output" }
  }
}
EOF
  printf 'local edits\n' > "${WORK}/TODO 1 - setup alpha.md"
  run_script >/dev/null
  assert_file "${WORK}/TODO 1 - setup alpha.md" $'local edits\n'
  run_script --yes >/dev/null
  assert_file "${WORK}/TODO 1 - setup alpha.md" $'from output\n'
  printf 'ok noninteractive overwrite rules\n'
}

test_unsafe_path_does_not_escape_workdir() {
  reset_work
  setup_fake_terraform
  write_fixture <<'EOF'
{
  "todo_files": {
    "sensitive": false,
    "type": ["map", "string"],
    "value": {
      "ok.md": "fine",
      "skip/me.md": "nope"
    }
  }
}
EOF
  set +e
  run_script --yes >/dev/null 2>&1
  local rc=$?
  set -e
  [[ "$rc" -eq 1 ]] || fail "unsafe path should exit 1, got ${rc}"
  assert_file "${WORK}/ok.md" $'fine\n'
  [[ ! -e "${WORK}/skip/me.md" && ! -d "${WORK}/skip" ]] || fail "unsafe path was created"
  printf 'ok rejects unsafe paths\n'
}

test_fallback_to_joined_todo_outputs() {
  reset_work
  setup_fake_terraform
  write_fixture <<'EOF'
{
  "todo_files": { "sensitive": false, "type": "string", "value": null },
  "todos_2": { "sensitive": false, "type": "string", "value": "second" },
  "todos_1": { "sensitive": false, "type": "string", "value": "first" },
  "caller_role_arn": { "sensitive": false, "type": "string", "value": "arn:aws:iam::1:role/x" }
}
EOF
  run_script --yes >/dev/null
  assert_file "${WORK}/todos_1.md" $'first\n'
  assert_file "${WORK}/todos_2.md" $'second\n'
  [[ ! -e "${WORK}/caller_role_arn.md" ]] || fail "unrelated output was written"
  printf 'ok falls back to todos_N outputs\n'
}

test_no_todo_content_exits_cleanly() {
  reset_work
  setup_fake_terraform
  write_fixture <<'EOF'
{ "caller_role_arn": { "sensitive": false, "type": "string", "value": "arn:aws:iam::1:role/x" } }
EOF
  local out
  out="$(run_script)"
  [[ "$out" == *"No TODO content found"* ]] || fail "missing empty-output message: ${out}"
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

test_prompt_no_keeps_and_default_yes_overwrites() {
  reset_work
  setup_fake_terraform
  write_fixture <<'EOF'
{
  "todo_files": {
    "sensitive": false,
    "type": ["map", "string"],
    "value": {
      "TODO 1 - setup alpha.md": "from output alpha",
      "TODO 2 - test beta.md": "from output beta"
    }
  }
}
EOF
  printf 'KEEP\n' > "${WORK}/TODO 1 - setup alpha.md"
  printf 'KEEP2\n' > "${WORK}/TODO 2 - test beta.md"

  WORK="$WORK" SCRIPT="$SCRIPT" FAKE_BIN="$FAKE_BIN" ORIG_PATH="$ORIG_PATH" python3 - <<'PY'
import os, pty, select, subprocess, sys
work = os.environ["WORK"]
script = os.environ["SCRIPT"]
env = os.environ.copy()
env["PATH"] = os.environ["FAKE_BIN"] + os.pathsep + os.environ["ORIG_PATH"]
env["TODO_FIXTURE"] = os.path.join(work, "fixture.json")
env["TERM"] = "xterm-256color"
master, slave = pty.openpty()
proc = subprocess.Popen(
    [script],
    stdin=slave,
    stdout=slave,
    stderr=slave,
    cwd=work,
    env=env,
)
os.close(slave)
buf = b""
# n keeps the first file; Enter accepts the default Y and overwrites the second.
answers = [b"n\n", b"\n"]
while True:
    ready, _, _ = select.select([master], [], [], 5)
    if not ready:
        proc.kill()
        sys.stderr.write(buf.decode(errors="replace"))
        raise SystemExit("timed out waiting for overwrite prompt")
    try:
        chunk = os.read(master, 4096)
    except OSError:
        break
    if not chunk:
        break
    buf += chunk
    if b"[Y/n]" in buf and answers:
        os.write(master, answers.pop(0))
        buf = buf.replace(b"[Y/n]", b"[asked]", 1)
rc = proc.wait()
if rc != 0:
    sys.stderr.write(buf.decode(errors="replace"))
    raise SystemExit(f"prompt run exited {rc}")
if b"Left 1" not in buf or b"Overwrote 1" not in buf:
    sys.stderr.write(buf.decode(errors="replace"))
    raise SystemExit("prompt summary did not report one skip and one overwrite")
PY
  assert_file "${WORK}/TODO 1 - setup alpha.md" $'KEEP\n'
  assert_file "${WORK}/TODO 2 - test beta.md" $'from output beta\n'
  printf 'ok overwrite prompt\n'
}

test_wrappers_match_and_find_implementation() {
  diff -q "$AWS_WRAPPER" "$GCP_WRAPPER" >/dev/null || fail "aws and gcp generate-todos.sh wrappers differ"

  local tree stub_out
  tree="$(mktemp -d)"
  mkdir -p "${tree}/example/.terraform/modules/psoxy/tools" "${tree}/tools"
  cp "$AWS_WRAPPER" "${tree}/example/generate-todos.sh"
  chmod +x "${tree}/example/generate-todos.sh"
  printf '#!/bin/bash\nprintf "MODULE_COPY\n"\n' > "${tree}/example/.terraform/modules/psoxy/tools/generate-todos.sh"
  printf '#!/bin/bash\nprintf "WALK_UP\n"\nexit 9\n' > "${tree}/tools/generate-todos.sh"
  chmod +x "${tree}/example/.terraform/modules/psoxy/tools/generate-todos.sh" "${tree}/tools/generate-todos.sh"

  stub_out="$("${tree}/example/generate-todos.sh" </dev/null)"
  [[ "$stub_out" == *"MODULE_COPY"* ]] || fail "wrapper did not use .terraform module copy: ${stub_out}"

  rm -rf "${tree}/example/.terraform"
  stub_out="$("${tree}/example/generate-todos.sh" </dev/null)" || true
  [[ "$stub_out" == *"WALK_UP"* ]] || fail "wrapper did not walk up to tools/generate-todos.sh: ${stub_out}"
  rm -rf "$tree"
  printf 'ok wrappers\n'
}

test_writes_todo_files_and_ignores_joined_blobs
test_noninteractive_keeps_existing_unless_yes
test_unsafe_path_does_not_escape_workdir
test_fallback_to_joined_todo_outputs
test_no_todo_content_exits_cleanly
test_refuses_to_run_outside_terraform_root
test_prompt_no_keeps_and_default_yes_overwrites
test_wrappers_match_and_find_implementation

printf 'all generate-todos tests passed\n'
