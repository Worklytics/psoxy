# Release QA

End-to-end QA before merging an `rc-vX.Y.Z` branch to `main`. Run from the repository root.

This supplements [releases.md](../../docs/development/releases.md) and automates the dev-example apply/test workflow described in [test_plan.md](test_plan.md).

## Prerequisites

- Branch `rc-vX.Y.Z` with release refs updated to `vX.Y.Z` (via `./tools/release/prep.sh`)
- CLI auth: `aws`, `gcloud` (+ application-default credentials), and `az` when `msft_tenant_id` is set in tfvars
- `gh` authenticated (for PR steps)
- `terraform` in PATH

## Quick start

```shell
./tools/release/run-release-qa.sh v0.6.6
```

Runs verify → apply (AWS, then GCP) → test-all (both) → summarize. Stops before creating the
release PR so you can review plan logs and connector summaries.

To also open the release PR and post results:

```shell
./tools/release/run-release-qa.sh v0.6.6 --create-pr --post-pr-results
```

## Workflow

Run steps **sequentially**. Do not apply AWS and GCP in parallel.

| Step | Action | Script |
|------|--------|--------|
| 1 | Verify release refs | `qa/verify-release-refs.sh` |
| 2 | Apply AWS example (review plan log) | `qa/apply-example.sh aws` |
| 3 | Apply GCP example (review plan log) | `qa/apply-example.sh gcp` |
| 4 | Run AWS connector tests | `qa/run-example-tests.sh aws` |
| 5 | Run GCP connector tests | `qa/run-example-tests.sh gcp` |
| 6 | Summarize connector state | `qa/summarize-connector-tests.sh` |
| 7 | Create release PR | `rc-to-main.sh` |
| 8 | Comment on PR + check off test plan | `qa/update-release-pr-results.sh` |

### Step 1: Verify release refs

If refs are not updated yet:

```shell
./tools/release/prep.sh rc-vX.Y.Z vX.Y.Z
```

Then:

```shell
./tools/release/qa/verify-release-refs.sh vX.Y.Z
```

### Steps 2–3: Apply dev examples

```shell
./tools/release/qa/apply-example.sh aws vX.Y.Z true
./tools/release/qa/apply-example.sh gcp vX.Y.Z true
```

Logs: `infra/examples-dev/{aws,gcp}/YYYYMMDD_{aws|gcp}-vX.Y.Z-{plan,apply}.txt`

Review plan logs for unexpected destroys or replacements before running tests.

### Steps 4–5: Connector tests

```shell
./tools/release/qa/run-example-tests.sh aws vX.Y.Z
./tools/release/qa/run-example-tests.sh gcp vX.Y.Z
```

Outputs: `infra/examples-dev/{aws,gcp}/YYYYMMDD_{aws|gcp}-vX.Y.Z-tests.txt`

Allow several minutes per cloud (Slack async, bulk uploads, llm-portal bucket polling).

### Step 6: Summarize

```shell
./tools/release/qa/summarize-connector-tests.sh aws infra/examples-dev/aws/...-tests.txt vX.Y.Z
./tools/release/qa/summarize-connector-tests.sh gcp infra/examples-dev/gcp/...-tests.txt vX.Y.Z
```

Each run writes:

- `*.summary.md` — markdown tables and category breakdown
- `*.checklist` — pass/fail per test-plan category (for PR checkbox updates)

#### Result statuses

| Status | Meaning |
|--------|---------|
| pass | Health + API/bulk/webhook verification succeeded |
| partial | Proxy healthy but upstream API rejected the call |
| fail | Missing secrets/config or connection setup error |

#### Test-plan categories

From [test_plan.md](test_plan.md). A category is checked off when at least one connector in that category passes (partial counts).

| Category | Example connectors |
|----------|-------------------|
| Microsoft API | `azure-ad`, `outlook-cal`, `msft-teams` |
| Google Workspace API | `gcal`, `gdirectory`, `google-chat`, `gmail`, `gemini-in-workspace-apps` |
| Token-based API | `asana`, `slack-analytics`, `zoom`, `jira-cloud`, `github`, … |
| API with async | `slack-analytics` |
| Webhook collector | `llm-portal` |
| Bulk connector | `hris`, `metrics`, `workdata-generic` |

Distinguish credential gaps (expected for unconfigured connectors) from proxy regressions in summaries.

### Step 7: Create release PR

On `rc-vX.Y.Z`:

```shell
./tools/release/rc-to-main.sh vX.Y.Z
```

Partially interactive (`npm audit fix` prompt). Note the PR number from output.

### Step 8: Post QA on the PR

```shell
./tools/release/qa/update-release-pr-results.sh \
  <PR_NUMBER> \
  infra/examples-dev/aws/...-tests.txt.checklist \
  infra/examples-dev/gcp/...-tests.txt.checklist \
  infra/examples-dev/aws/...-tests.txt.summary.md \
  infra/examples-dev/gcp/...-tests.txt.summary.md
```

Posts a comment with both summaries and checks off test-plan items in the PR body.

### After merge

```shell
./tools/release/publish.sh vX.Y.Z
```

## Troubleshooting

| Issue | Action |
|-------|--------|
| `verify-release-refs.sh` fails | Run `./tools/release/prep.sh rc-vX.Y.Z vX.Y.Z` |
| Apply auth errors | `./az-auth`, `aws sso login`, `gcloud auth application-default login` |
| `missingConfigProperties` in health check | Unconfigured secrets; note in summary, not a proxy bug |
| `msft-teams` 401 while `azure-ad` works | Azure Graph permissions/consent |
| `rc-to-main.sh` branch error | `git checkout rc-vX.Y.Z` |

## Scripts

| Script | Purpose |
|--------|---------|
| [run-release-qa.sh](run-release-qa.sh) | Orchestrates the QA workflow |
| [qa/verify-release-refs.sh](qa/verify-release-refs.sh) | Confirm rc → v ref migration |
| [qa/apply-example.sh](qa/apply-example.sh) | Plan + apply with logs |
| [qa/run-example-tests.sh](qa/run-example-tests.sh) | Run `test-all.sh`, capture output |
| [qa/summarize-connector-tests.sh](qa/summarize-connector-tests.sh) | Parse test output → markdown |
| [qa/update-release-pr-results.sh](qa/update-release-pr-results.sh) | PR comment + checkbox update |
