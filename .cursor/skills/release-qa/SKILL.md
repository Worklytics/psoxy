---
name: release-qa
description: |
  Runs pre-release QA for Psoxy: verify release refs (rc-vX.Y.Z → vX.Y.Z), apply AWS and GCP
  dev examples sequentially, run test-all.sh for both, summarize connector status, create the
  rc-to-main release PR, and post QA results on that PR. Use when cutting a release, running
  release QA, merging rc-v to main, or when the user asks to test connectors before publish.
---

# Release QA

End-to-end release QA for the Psoxy repo on an `rc-vX.Y.Z` branch that has been prepared for release (`./tools/release/prep.sh rc-vX.Y.Z vX.Y.Z`).

## Prerequisites

- On branch `rc-vX.Y.Z` with release refs already updated to `vX.Y.Z`
- Authenticated: `aws`, `gcloud` (+ ADC), and `az` (if `msft_tenant_id` in tfvars)
- `gh` CLI authenticated
- `terraform` available in PATH
- Repo root as working directory unless noted

Derive `RELEASE` from the branch (`rc-v0.6.6` → `v0.6.6`) or accept it from the user.

## Workflow checklist

```
Release QA progress:
- [ ] Step 1: Verify release refs
- [ ] Step 2: Apply AWS example (review plan log)
- [ ] Step 3: Apply GCP example (review plan log)
- [ ] Step 4: Run test-all on AWS
- [ ] Step 5: Run test-all on GCP
- [ ] Step 6: Summarize connector results
- [ ] Step 7: Create release PR (rc-to-main)
- [ ] Step 8: Post PR comment + check off test plan
```

Run steps **sequentially**. Do not apply AWS and GCP in parallel.

---

## Step 1: Verify release refs

If refs are not yet updated, run prep first (interactive):

```bash
./tools/release/prep.sh rc-vX.Y.Z vX.Y.Z
```

Then verify:

```bash
./tools/release/qa/verify-release-refs.sh vX.Y.Z
```

Stop if verification fails. Fix with `prep.sh` or manual ref updates before continuing.

---

## Step 2–3: Apply dev examples (sequential)

Use the non-interactive helper (runs `terraform plan` then `terraform apply`, logs both):

```bash
./tools/release/qa/apply-example.sh aws vX.Y.Z true
# Review plan log printed path; confirm apply succeeded before continuing

./tools/release/qa/apply-example.sh gcp vX.Y.Z true
```

Logs land in `infra/examples-dev/{aws,gcp}/YYYYMMDD_{aws|gcp}-vX.Y.Z-{plan,apply}.txt`.

**Review the plan logs** and call out unexpected destroys/replacements before running tests.

`force_bundle=true` rebuilds the JAR (appropriate for release QA after Java changes).

---

## Step 4–5: Run connector tests

```bash
./tools/release/qa/run-example-tests.sh aws vX.Y.Z
./tools/release/qa/run-example-tests.sh gcp vX.Y.Z
```

Outputs: `infra/examples-dev/{aws,gcp}/YYYYMMDD_{aws|gcp}-vX.Y.Z-tests.txt`

Tests can take several minutes each (Slack async, bulk uploads, llm-portal bucket polling).

---

## Step 6: Summarize connector state

```bash
./tools/release/qa/summarize-connector-tests.sh aws infra/examples-dev/aws/YYYYMMDD_aws-vX.Y.Z-tests.txt vX.Y.Z \
  > /tmp/aws-qa-summary.md

./tools/release/qa/summarize-connector-tests.sh gcp infra/examples-dev/gcp/YYYYMMDD_gcp-vX.Y.Z-tests.txt vX.Y.Z \
  > /tmp/gcp-qa-summary.md
```

Each command also writes sidecar files:

- `*.summary.md` — markdown tables + category breakdown
- `*.checklist` — machine-readable pass/fail per test-plan category

Status meanings:

| Status | Meaning |
|--------|---------|
| **pass** | Health + API/bulk/webhook verification succeeded |
| **partial** | Proxy healthy but upstream API rejected the call |
| **fail** | Missing secrets/config or connection setup error |

Test-plan categories (from `tools/release/test_plan.md`):

| Category | Example connectors |
|----------|-------------------|
| Microsoft API | `azure-ad`, `outlook-cal`, `msft-teams` |
| Google Workspace API | `gcal`, `gdirectory`, `google-chat`, `gmail`, `gemini-in-workspace-apps` |
| Token-based API | `asana`, `slack-analytics`, `zoom`, `jira-cloud`, `github`, … |
| API with async | `slack-analytics` |
| Webhook collector | `llm-portal` |
| Bulk connector | `hris`, `metrics`, `workdata-generic` |

A category is checked off when **at least one** connector in that category passes (partial counts for PR checkboxes).

Present the user a combined summary before opening the PR. Note credential gaps vs real regressions.

---

## Step 7: Create release PR

Must be on `rc-vX.Y.Z`:

```bash
git checkout rc-vX.Y.Z
./tools/release/rc-to-main.sh vX.Y.Z
```

`rc-to-main.sh` is partially interactive (`npm audit fix` prompt). Answer `y` to continue unless dependency changes need a separate PR.

Capture the PR URL/number from script output.

---

## Step 8: Post results on the release PR

```bash
PR_NUMBER=...  # from rc-to-main.sh output

./tools/release/qa/update-release-pr-results.sh \
  "$PR_NUMBER" \
  infra/examples-dev/aws/YYYYMMDD_aws-vX.Y.Z-tests.txt.checklist \
  infra/examples-dev/gcp/YYYYMMDD_gcp-vX.Y.Z-tests.txt.checklist \
  infra/examples-dev/aws/YYYYMMDD_aws-vX.Y.Z-tests.txt.summary.md \
  infra/examples-dev/gcp/YYYYMMDD_gcp-vX.Y.Z-tests.txt.summary.md
```

This:

1. Posts a PR comment with both AWS and GCP connector summaries
2. Checks off `- [x]` items under `### AWS` and `### GCP` in the PR body for categories that passed (including partial)

---

## After merge

Remind the user:

```bash
./tools/release/publish.sh vX.Y.Z
```

---

## Troubleshooting

| Issue | Action |
|-------|--------|
| `verify-release-refs.sh` fails | Run `./tools/release/prep.sh rc-vX.Y.Z vX.Y.Z` |
| Apply auth errors | Re-run `./az-auth`, `aws sso login`, `gcloud auth application-default login` |
| Connector fails with `missingConfigProperties` | Expected for unconfigured secrets; note in summary, not a proxy regression |
| `msft-teams` 401 while `azure-ad` works | Azure Graph permissions/consent issue |
| `rc-to-main.sh` branch error | Checkout `rc-vX.Y.Z` first |

## Helper scripts

| Script | Purpose |
|--------|---------|
| `tools/release/qa/verify-release-refs.sh` | Confirm rc → v ref migration |
| `tools/release/qa/apply-example.sh` | Plan + apply with logs |
| `tools/release/qa/run-example-tests.sh` | Run `test-all.sh`, capture output |
| `tools/release/qa/summarize-connector-tests.sh` | Parse test output → markdown |
| `tools/release/qa/update-release-pr-results.sh` | PR comment + checkbox update |
