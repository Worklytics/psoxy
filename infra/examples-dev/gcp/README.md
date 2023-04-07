# example-google-workspace
** alpha quality example atm **

A Terraform root module to provision GCP project for Psoxy, configure it, and create necessary infra
for connections to all supported Google Workspace sources, with state stored to local filesystem.
As such, it is not appropriate for scenario with multiple developers. As state will contain
sensitive information (eg, service account keys), care should be taken in production to ensure that
the filesystem in question is secure or another Terraform backend should be used (eg, GCS bucket
encrypted with a CMEK).

## Usage

### Prereqs

You'll need:
- a Bash-like shell environment on Linux, MacOS, or [WSL on Windows](https://learn.microsoft.com/en-us/windows/wsl/install).
- [`git` installed](https://git-scm.com/book/en/v2/Getting-Started-Installing-Git), although it
  is usually included on those platforms (check with `git --version`).
- an GCP project account and credentials, as described in [Psoxy's Getting Started - GCP docs](https://github.com/Worklytics/psoxy/blob/v0.4.17/docs/gcp/getting-started.md)
- the [prerequisites for Psoxy](https://github.com/Worklytics/psoxy/blob/v0.4.17/README.md#prerequisites)
  itself, although this example will attempt to help you check those

### Setup

See [GitHub's documentation](https://help.github.com/en/github/creating-cloning-and-archiving-repositories/creating-a-repository-from-a-template)
for more details.

1. Clone the git repo containing this example:
```shell

git clone https://github.com/Worklytics/psoxy.git
cd psoxy
```

2. Make a copy of this example for your organization, and go into that directory:
```shell

cp -r infra/examples/gcp infra/examples/{{YOUR_ORG_NAME}}

cd infra/examples/{{YOUR_ORG_NAME}}
```

3. Check your prereqs. Review versions and install anything needed.

```shell
./check-prereqs
```

4. Authenticate your tools as needed:

- [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-configure.html) - `aws get-caller-identity` should work and return your expected account/user
- if plan to get data from Google Workspace, auth [GCloud CLI](https://cloud.google.com/sdk/docs/authorizing) - `gcloud auth login` to authenticate, then `gcloud auth list` to verify you have expected account/user
- if plan to get data from Microsoft 365, auth [Azure CLI](https://docs.microsoft.com/en-us/cli/azure/authenticate-azure-cli) - `az login --allow-no-subscription` to authenticate, then `az account list` to verify you have expected account/user

5. Initialize your configuration

```shell
./init
```

6. Review your `terraform.tfvars` file; customize as needed (eg, comment out datasources you don't need).

7. Run `terraform plan` and review results to understand what will be created. Customize your
   `terraform.tfvars` or `main.tf` file if needed.

```shell
terraform plan
```

8. Run `terraform apply` to create the resources.
```shell
terraform apply
```
