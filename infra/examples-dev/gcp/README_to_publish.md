# Psoxy Deployment Example - GCP

[![Latest Release](https://img.shields.io/github/v/release/Worklytics/psoxy-example-gcp)](https://github.com/Worklytics/psoxy-example-gcp/releases/latest)
![build passing](https://img.shields.io/github/actions/workflow/status/Worklytics/psoxy-example-gcp/terraform_validate.yaml?label=build%20passing)
![tfsec](https://img.shields.io/github/actions/workflow/status/Worklytics/psoxy-example-gcp/tfsec.yml?label=tfsec)

This is a template repo for a Terraform configuration that deploys the [Worklytics pseudonymization proxy (psoxy)](https://github.com/Worklytics/psoxy) on GCP.

## Usage

### Prereqs

You'll need:
- a Bash-like shell environment on Linux, MacOS, or [WSL on Windows](https://learn.microsoft.com/en-us/windows/wsl/install).
- [`git` installed](https://git-scm.com/book/en/v2/Getting-Started-Installing-Git), although it is usually included on those platforms (check with `git --version`).
- an GCP project account and credentials, as described in [Psoxy's Getting Started - GCP docs](https://github.com/Worklytics/psoxy/blob/v0.5.9/docs/gcp/getting-started.md)
- the [prerequisites for Psoxy](https://github.com/Worklytics/psoxy/blob/v0.5.9/README.md#prerequisites) itself, although this example will attempt to help you check those

### Setup

See [GitHub's documentation](https://help.github.com/en/github/creating-cloning-and-archiving-repositories/creating-a-repository-from-a-template) for more details.

1. Click the 'Use this template' button in the upper right corner of [this page](https://github.com/Worklytics/psoxy-example-gcp).
    - choose a name for your copy of this repo
    - leave "Include all branches" unchecked

Clone the resulting repo to your machine.  Example command below, just fill in your org and repo names.

```shell
git clone https://github.com/{{YOUR_ORG_ID}}/{{YOUR_REPO_NAME}}.git
```

- Alternatively **use template outside GitHub** (if you *cannot* 'Use this template', perhaps because your organization doesn't use GitHub or you need to use a different git host, you can manually create a copy:
   - Clone this repository to your local machine:
     ```shell
     git clone https://github.com/Worklytics/psoxy-example-gcp.git
     cd psoxy-example-gcp
     ```
   - Remove the existing git history to start fresh:
     ```shell
     rm -rf .git
     ```
   - Initialize a new git repository:
     ```shell
     git init
     git add .
     git commit -m "Initial commit from psoxy-example-gcp template"
     ```
   - Create a new repository on your preferred git hosting service (GitLab, Bitbucket, etc.)
   - Add your new repository as the remote origin:
     ```shell
     git remote add origin https://your-git-host.com/your-org/your-repo-name.git
     git branch -M main
     git push -u origin main
     ```

- Alternatively, **use this in a monorepo** (eg, you maintain have a monorepo with lots of terraform configurations, and you want to add this to those)
  - Clone this repository to your local machine and copy its contents (excluding hidden stuff like `.git` files into your monorepo)
     ```shell
     git clone https://github.com/Worklytics/psoxy-example-gcp.git
     rm -rf psoxy-example-gcp/.git
     cp -r psoxy-example-gcp ${PATH_TO_MONO_REPO}/
     ```

2. Check your prereqs. Review versions and install anything needed.

```shell
./check-prereqs
```

4. Authenticate your tools as needed:

  - auth [GCloud CLI](https://cloud.google.com/sdk/docs/authorizing) - `gcloud auth login` to authenticate, then `gcloud auth list` to verify you have the expected account/user
  - auth [Azure CLI](https://learn.microsoft.com/en-us/cli/azure/authenticate-azure-cli) if using Microsoft 365 data sources  - `az login --allow-no-subscription` to authenticate, then `az account list` to verify you have the expected account/user

5. Initialize your configuration using our helper script. Follow the prompts.

```shell
./init
```


6. Review your `terraform.tfvars` file and `main.tf`; customize as needed (eg, comment out datasources you don't need).

   In particular, if you're NOT using Google Workspace as a data source, remove (delete) the `.tf` files named `google-*.tf` AND references to values from those files from the `main.tf` file. (Our `./init` script *should* have removed these for you)

   Similiarly, if you're NOT using Microsoft 365 as a data source, remove (delete) the `.tf` files named `msft-365-*.tf` AND references to values from those files from the `main.tf` file. (Our `./init` script *should* have removed these for you)


7. Run `terraform plan` and review results to understand what will be created. Customize your `terraform.tfvars` or `main.tf` file if needed. (or push to your CI/CD system, if not running locally)

```shell
terraform plan
```

8. Run `terraform apply` to create the resources. (or push to your CI/CD system to do this automatically)

```shell
terraform apply
```

9. The above steps have created or modified various files that you should commit a code repository or otherwise preserve. In particular `terraform.tfvars`, `main.tf`, `terraform.tfstate` (if you ran `terraform` locally) and `.terraform.lock.hcl` should be preserved. Please do `git add` for each and then `git commit` to save your changes.

## License

The source code contained in this repo is licensed under the [Apache License, Version 2.0](LICENSE).

Usage of terraform, psoxy, or other tooling invoked by scripts in this repo or described in the example tutorials it contains are each subject to their own license terms.

## Support

This example repo is maintained by [Worklytics](https://worklytics.co). Paid support is available. Please contact [sales@worklytics.co](mailto:sales@worklytics.co).
