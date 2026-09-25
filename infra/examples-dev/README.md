# Examples - Dev

Unlike the published customer example template repositories ([AWS](https://github.com/Worklytics/psoxy-example-aws), [GCP](https://github.com/Worklytics/psoxy-example-gcp)), this directory contains examples intended for development
purposes, referencing Psoxy-provided modules locally, rather than the published versions in GitHub.

Each example includes a `reset-example` symlink to [`tools/reset-example.sh`](../../tools/reset-example.sh) for resetting local IaC state during development (back up / recover `terraform.tfvars`, etc.).

`./build-tests.sh` in each example root generates `test-*.sh` and `test-all.sh` from Terraform outputs by running `tools/build-test-scripts-from-output.sh` from the module checkout under `.terraform/modules/` (or the repo above the example, when that checkout is not a full clone).
