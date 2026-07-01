#!/bin/bash
# Summarize connector test output from test-all.sh into markdown + checklist metadata.
# Usage: ./tools/release/qa/summarize-connector-tests.sh <cloud> <test-output-file> [release]
# Writes: <test-output-file>.summary.md and <test-output-file>.checklist
# Prints the markdown summary to stdout.

set -euo pipefail

CLOUD="${1:-}"
INPUT="${2:-}"
RELEASE="${3:-}"

if [ -z "$CLOUD" ] || [ -z "$INPUT" ]; then
  echo "Usage: $0 <aws|gcp> <test-output-file> [release]" >&2
  exit 1
fi

if [ ! -f "$INPUT" ]; then
  echo "File not found: $INPUT" >&2
  exit 1
fi

SUMMARY_FILE="${INPUT}.summary.md"
CHECKLIST_FILE="${INPUT}.checklist"

# Strip ANSI color codes for parsing.
CLEAN_INPUT="$(mktemp)"
trap 'rm -f "$CLEAN_INPUT"' EXIT
sed -E 's/\x1B\[[0-9;]*[[:alpha:]]//g' "$INPUT" > "$CLEAN_INPUT"

python3 - "$CLOUD" "$CLEAN_INPUT" "$RELEASE" "$SUMMARY_FILE" "$CHECKLIST_FILE" <<'PY'
import re
import sys
from pathlib import Path

cloud, input_path, release, summary_path, checklist_path = sys.argv[1:6]
text = Path(input_path).read_text(errors="replace")

MSFT = {"azure-ad", "outlook-cal", "msft-teams"}
GOOGLE = {"gcal", "gdirectory", "google-chat", "gmail", "gemini-in-workspace-apps"}
TOKEN = {
    "asana", "slack-analytics", "chatgpt-enterprise", "cursor", "zoom",
    "jira-cloud", "github", "github-copilot",
}
ASYNC = {"slack-analytics"}
WEBHOOK = {"llm-portal"}
BULK = {"hris", "metrics", "workdata-generic"}

CATEGORY_LABELS = {
    "microsoft": "Microsoft API connector",
    "google_workspace": "Google Workspace API connector",
    "token": "Token-based API connector",
    "async": "API connector with async",
    "webhook": "Webhook collector",
    "bulk": "Bulk connector",
}


def normalize_name(raw: str) -> str:
    raw = re.sub(r"\[[0-9;]*m", "", raw).strip()
    raw = re.sub(r"\s+\.\.\.$", "", raw).strip()
    for prefix in ("dev-erik-awsall-", "psoxy-dev-erik-"):
        if raw.startswith(prefix):
            raw = raw[len(prefix):]
    return raw


def connector_status(block: str, name: str) -> str:
    lower = block.lower()
    if name in BULK:
        if "file downloaded" in lower and "file uploaded" in lower:
            return "pass"
        return "fail"
    if name in WEBHOOK:
        if "verification successful" in lower:
            return "pass"
        return "fail"

    health_ok = bool(re.search(r"health check result:\s*ok", lower))
    health_fail = bool(re.search(r"health check result:\s*precondition failed", lower))
    call_ok = bool(re.search(r"call result:\s*ok", lower))
    call_fail = bool(re.search(r"call result:.*(error|failed)", lower))
    missing = re.findall(r'"missingConfigProperties":\s*\[\s*"([^"]+)"', block)

    if name in ASYNC and "async response content" in lower and health_ok and call_ok:
        return "pass"

    if health_ok and call_ok:
        return "pass"
    if health_ok and call_fail:
        return "partial"
    if health_fail or missing:
        return "fail"
    if call_fail:
        return "fail"
    return "unknown"


parts = re.split(r"(?=Quick test of )", text)
connectors = []
for part in parts:
    m = re.match(r"Quick test of (.+)", part)
    if not m:
        continue
    name = normalize_name(m.group(1))
    status = connector_status(part, name)
    connectors.append((name, status))

if not connectors:
    print(f"No connector tests found in {input_path}", file=sys.stderr)
    sys.exit(1)

status_icon = {"pass": "✅", "partial": "⚠️", "fail": "❌", "unknown": "❓"}
counts = {"pass": 0, "partial": 0, "fail": 0, "unknown": 0}
for _, status in connectors:
    counts[status] = counts.get(status, 0) + 1


def category_status(category_ids: set) -> str:
    tested = [(n, s) for n, s in connectors if n in category_ids]
    if not tested:
        return "skip"
    if any(s == "pass" for _, s in tested):
        return "pass"
    if any(s == "partial" for _, s in tested):
        return "partial"
    return "fail"


categories = {key: category_status(ids) for key, ids in {
    "microsoft": MSFT,
    "google_workspace": GOOGLE,
    "token": TOKEN,
    "async": ASYNC,
    "webhook": WEBHOOK,
    "bulk": BULK,
}.items()}

release_line = f"**Release:** `{release}`  \n" if release else ""
lines = [
    f"### {cloud.upper()} connector QA",
    "",
    release_line.rstrip(),
    "",
    (
        f"**Summary:** {counts['pass']} passing, {counts['partial']} partial, "
        f"{counts['fail']} failing"
        + (f", {counts['unknown']} unknown" if counts['unknown'] else "")
        + f" (of {len(connectors)} tested)"
    ),
    "",
    "| Connector | Status |",
    "|-----------|--------|",
]
for name, status in connectors:
    note = ""
    if status == "partial":
        note = " (health OK, API issue)"
    elif status == "fail":
        note = " (not configured or setup error)"
    lines.append(f"| **{name}** | {status_icon.get(status, '❓')} {status}{note} |")

lines.extend(["", "#### Test plan categories", ""])
for key, label in CATEGORY_LABELS.items():
    cat = categories[key]
    if cat == "skip":
        lines.append(f"- ⏭️ {label} (not tested in this example)")
    elif cat == "pass":
        lines.append(f"- ✅ {label}")
    elif cat == "partial":
        lines.append(f"- ⚠️ {label} (partial)")
    else:
        lines.append(f"- ❌ {label}")

summary = "\n".join(lines) + "\n"
Path(summary_path).write_text(summary)
Path(checklist_path).write_text(
    "\n".join(f"{cloud} {key} {categories[key]}" for key in CATEGORY_LABELS) + "\n"
)
print(summary, end="")
PY
