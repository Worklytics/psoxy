# General Troubleshooting

## Specific Data Sources

- [Microsoft 365](sources/microsoft-365/README.md)

### Error: Attempted to load application default credentials (Google provider authentication failure)

This is related to `gcloud` not being authenticated (or installed?) in the environment where you're running terraform, which the `google` terraform provider requires.

If you DO NOT intend to use Google Workspace as a data source, you should do the following:
  - remove the `google-*.tf` files from your terraform configuration
  - remove module/local references from your `main.tf` file that referred to those files; as of `v0.4.53`, there are 3 such references you must remove; you will get errors in terraform commands until you remove all of them. The error messages should reference the impacted line numbers.

If you DO intend to use Google Workspace as a data source, you must install and authenticate the `gcloud` CLI and/or modify the `google` provider block in `google-workspace.tf` with your desired authentication details. See: [Google Terraform Provider](https://registry.terraform.io/providers/hashicorp/google/latest/docs/guides/provider_reference)

## Specific Host platforms:

- [AWS](aws/troubleshooting.md)
- [GCP](gcp/troubleshooting.md)

## General Tips

### Verify Pre-Requisites

Our example templates include a script to check for the prerequisites for running Psoxy. You can run this prior to `./init` to get feedback/suggestions on what prerequisites you may be missing and how to install them.

```shell
./check-prereqs
```

### General Build / Packaging Failures

Our example Terraform configurations should compile and package the Java code into a JAR file, which is then deployed by Terraform to your host environment.

This is done via a build script, invoked by a Terraform module (see [`modules/psoxy-package`](../infra/modules/psoxy-package)).

If, on your first `terraform plan`/`terraform apply`, you see the line such as

`module.psoxy-aws-msft-365.module.psoxy-aws.module.psoxy-package.data.external.deployment_package: Reading...`

And that returns really quickly, something may have gone wrong with the build. You can trigger the build directly by running:

```bash
# from the root of your checkout of the repository
./tools/build.sh
```

That may give you some clues as to what went wrong.

You can also look for a file called `last-build.log` in the directory where your Terraform configuration resides.

If you want to go step-by-step, you can run the following commands:

```bash
# from the root of your checkout of the repository
cd java/gateway-core
mvn package install
cd ../core
mvn package install
cd ../impl/aws
mvn package
```

Some problems we've seen:

- **Maven repository access** - the build process must get various dependencies from a remote Maven respository; if your laptop cannot reach Maven Central, is configured to get dependencies from some other Maven repository, etc - you might need to fix this issue. You can check your `~/.m2/settings.xml` file, which might give you some insight into what Maven repository you're using. It's also where you'd configure credentials for a private Maven repository, such as Artifactory/etc - so make sure those are correct.

### Upgrading Psoxy Code

If you upgrade your Psoxy code, it may be worth trying `terraform init --upgrade` to make sure you have the latest versions of all Terraform providers on which our configuration depends.

By default, terraform locks providers to the version that was the latest when you first ran `terraform init`. It does not upgrade them unless you explicitly instruct it to. It will not prompt you to upgrade them unless we update the version constraints in our modules.

While we strive to ensure accurate version constraints, and use provider features consistent with these constraints, our automated tests will run with the _latest_ version of each provider. Regretably, we don't currently have a way to test with ALL versions of each provider that satisfy the constraints, or all possible combinations of provider versions.

### State Inconsistencies

Often, in response to errors, a second run of `terraform apply` will work.

If something was _actually_ created in the cloud provider, but Terraform state doesn't reflect it, then try `terraform import [resource] [provider-resource-id]`. `[resource]` should be replaced with whatever the path to it is in your terraform configuration, which you can get from the `terraform plan` output. `provider-resource-id` is a little trickier, and you might need to find the format required by finding the Terraform docs for the resource type on the web.

NOTE: resources in plan with brackets/quotes will need these escaped with a backslash for use in bash commands.

eg

```shell
terraform import module.psoxy-msft-connector\[\"outlook-cal\"\].aws_lambda_function_url.lambda_url psoxy-outlook-cal
```

### Unsupported Terraform versions

Errors such as the following on `terraform plan`?
```shell
Module module.psoxy (from git::https://github.com/worklytics/psoxy//infra/modules/gcp-host?ref=v0.4.51) does not support Terraform version 1.8.1. To proceed, either choose another supported Terraform version or update
│ this version constraint. Version constraints are normally set for good reason, so updating the constraint may lead to other errors or unexpected behavior.
```

The solution is to downgrade your Terraform version to one that's supported by our modules (>= 1.3.x, <= 1.7.x as of March 2024).

_If you're running Terraform in cloud/CI environment,_ including Terraform Cloud, GitHub Actions, etc, you can likely explicitly set the desired Terraform version in your workspace settings / terraform setup action.

_If you're running Terraform on your laptop or in a VM,_ use your package manager to downgrade or something like [`tfenv`](https://github.com/tfutils/tfenv) to concurrently use distinct Terraform versions on the machine. (set version <= 1.7.x in `.terraform-version` file in the root of your Terraform configuration for the proxy).
