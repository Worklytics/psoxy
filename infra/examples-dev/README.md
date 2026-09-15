# Examples - Dev

Unlike the published customer example template repositories ([AWS](https://github.com/Worklytics/psoxy-example-aws), [GCP](https://github.com/Worklytics/psoxy-example-gcp)), this directory contains examples intended for development
purposes, referencing Psoxy-provided modules locally, rather than the published versions in GitHub.

Each example includes a `reset-example` symlink to [`tools/reset-example.sh`](../../tools/reset-example.sh) for resetting local IaC state during development (back up / recover `terraform.tfvars`, etc.).

Use `./apply` (not a bare `terraform apply`) so auth and backend checks run first. That helper calls [`tools/examples-dev-apply-preflight.sh`](../../tools/examples-dev-apply-preflight.sh), which requires `terraform.tfvars` and `backend.tf`, confirms the initialized Terraform backend matches `backend.tf` (prefer a remote backend so worktrees share state), and checks AWS, Google, and Azure CLI auth only when that example actually needs them. A GCS backend needs Google ADC even on the AWS example. Microsoft 365 (`msft_tenant_id`) needs Azure CLI, preferably sandboxed via `./az-auth` / `./auth` into `.azure`. It also installs `tools/psoxy-test` npm deps in this worktree when they are missing; Terraform state from another worktree is not enough, because `node_modules` is local and gitignored.
