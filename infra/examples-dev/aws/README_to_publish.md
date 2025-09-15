# Psoxy for Worklytics Example - AWS


[![Latest Release](https://img.shields.io/github/v/release/Worklytics/psoxy-example-aws)](https://github.com/Worklytics/psoxy-example-aws/releases/latest)
![build passing](https://img.shields.io/github/actions/workflow/status/Worklytics/psoxy-example-aws/terraform_validate.yaml?label=build%20passing)
![tfsec](https://img.shields.io/github/actions/workflow/status/Worklytics/psoxy-example-aws/tfsec.yml?label=tfsec)

This is a template repo for a Terraform configuration that deploys the [Worklytics pseudonymization proxy (psoxy)](https://github.com/Worklytics/psoxy) on AWS.

## Usage

This is a template repo.  To use it, follow the instructions below.

### Prereqs

You'll need:
  - a Bash-like shell environment on Linux, MacOS, or [WSL on Windows](https://learn.microsoft.com/en-us/windows/wsl/install).
  - [`git` installed](https://git-scm.com/book/en/v2/Getting-Started-Installing-Git), although it
    is usually included on those platforms (check with `git --version`).
  - an AWS account and credentials, as described in [Psoxy's AWS - Getting Started docs](https://github.com/Worklytics/psoxy/blob/v0.4.37/docs/aws/getting-started.md)
  - the [prerequisites for Psoxy](https://github.com/Worklytics/psoxy/blob/v0.4.37/README.md#prerequisites)
    itself, although this example will attempt to help you check those.

### Getting Started

See [GitHub's documentation](https://help.github.com/en/github/creating-cloning-and-archiving-repositories/creating-a-repository-from-a-template)
for more details.

 1. Click the 'Use this template' button in the upper right corner of [this page](https://github.com/Worklytics/psoxy-example-aws).
    - choose a name for your copy of this repo
    - leave "Include all branches" unchecked

Clone the resulting repo to your machine.  Example command below, just fill in your org and repo names.

```shell
git clone https://github.com/{{YOUR_ORG_ID}}/{{YOUR_REPO_NAME}}.git
```

1a. **Manual template setup** (if you *cannot* 'Use this template', perhaps because your organization doesn't use GitHub or you need to use a different git host, you can manually create a copy:
   - Clone this repository to your local machine:
     ```shell
     git clone https://github.com/Worklytics/psoxy-example-aws.git
     cd psoxy-example-aws
     ```
   - Remove the existing git history to start fresh:
     ```shell
     rm -rf .git
     ```
   - Initialize a new git repository:
     ```shell
     git init
     git add .
     git commit -m "Initial commit from psoxy-example-aws template"
     ```
   - Create a new repository on your preferred git hosting service (GitLab, Bitbucket, etc.)
   - Add your new repository as the remote origin:
     ```shell
     git remote add origin https://your-git-host.com/your-org/your-repo-name.git
     git branch -M main
     git push -u origin main
     ```

2. Check your prereqs. Review versions and install anything needed.

```shell
./check-prereqs
```

3. Authenticate your tools as needed:

   - [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-configure.html) - `aws get-caller-identity` should work and return your expected account/user
   - if plan to get data from Google Workspace, auth [GCloud CLI](https://cloud.google.com/sdk/docs/authorizing) - `gcloud auth login` to authenticate, then `gcloud auth list` to verify you have expected account/user
   - if plan to get data from Microsoft 365, auth [Azure CLI](https://docs.microsoft.com/en-us/cli/azure/authenticate-azure-cli) - `az login --allow-no-subscription` to authenticate, then `az account list` to verify you have expected account/user

4. Initialize your configuration

```shell
./init
```

5. Review your `terraform.tfvars` file and `main.tf`; customize as needed (eg, comment out datasources you don't need).

   In particular, if you're NOT using Google Workspace as a data source, remove (delete) the `.tf` files named `google-*.tf` AND references to values from those files from the `main.tf` file. (Our `./init` script *should* have removed these for you)

   Similiarly, if you're NOT using Microsoft 365 as a data source, remove (delete) the `.tf` files named `msft-365-*.tf` AND references to values from those files from the `main.tf` file. (Our `./init` script *should* have removed these for you)


6. Run `terraform plan` and review results to understand what will be created. Customize your `terraform.tfvars` or `main.tf` file if needed. (or push to your CI/CD system, if not running locally)

```shell
terraform plan
```

7. Run `terraform apply` to create the resources. (or push to your CI/CD system to do this automatically)

```shell
terraform apply
```

8. The above steps have created or modified various files that you should commit a code repository or otherwise preserve. In particular `terraform.tfvars`, `main.tf`, `terraform.tfstate` (if you ran `terraform` locally) and `.terraform.lock.hcl` should be preserved. Please do `git add` for each and then `git commit` to save your changes.

## License

The source code contained in this repo is licensed under the [Apache License, Version 2.0](LICENSE).

Usage of terraform, psoxy, or other tooling invoked by scripts in this repo or described in the example tutorials it contains are each subject to their own license terms.

## Support

This example repo is maintained by [Worklytics](https://worklytics.co). Paid support is available. Please contact [sales@worklytics.co](mailto:sales@worklytics.co).
