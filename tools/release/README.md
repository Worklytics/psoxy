# Release scripts remaining in psoxy

Cut, QA, and local publish orchestration live in the internal `Worklytics/proxy-dev` repo (`tools/release/`). Run those scripts against a psoxy checkout.

This directory keeps only the scripts that **GitHub Actions in this repo** still invoke on tag / `rc-*` pushes:

| Script | Workflow |
|--------|----------|
| `publish-mvn-artifacts.sh` | `publish-release-artifacts.yaml` |
| `generate-sbom.sh` | `publish-release-artifacts.yaml` |
| `lib/publish-aws-bundle.sh` | `publish-aws-bundle.yaml` |
| `lib/publish-gcp-bundle.sh` | `publish-gcp-bundle.yaml` |
| `lib/deployment-bundle-release-notes-block.sh` | AWS/GCP bundle workflows |
| `lib/amend-github-release-notes.sh` | AWS/GCP bundle workflows |
| `verify-bundles.sh` | `publish-bundles.yaml` |
| `example-copy.sh` | `publish-example.yaml` |

Do not add cut/QA helpers here; they belong in proxy-dev so they are not shipped to customers who consume this repo via Terraform.
