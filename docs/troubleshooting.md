# General Troubleshooting

## Specific Data Sources

- [Microsoft 365](docs/sources/msft-365/troubleshooting.md)

## Specific Host platforms:

- [AWS](docs/aws/troubleshooting.md)
- [GCP](docs/gcp/troubleshooting.md)

## General Tips

### General Build / Packaging Failures

Our example Terraform configurations should compile and package the Java code into a JAR file, which
is then deployed by Terraform to your host environment.

This is done via a build script, invoked by a Terraform module (see
[`modules/psoxy-package`](../infra/modules/psoxy-package)).

If, on your first `terraform plan`/`terraform apply`, you see the line such as

`module.psoxy-aws-msft-365.module.psoxy-aws.module.psoxy-package.data.external.deployment_package: Reading...`

And that returns really quickly, something may have gone wrong with the build. You can trigger the
build directly by running:

```bash
# from the root of your checkout of the repository
./tools/build.sh
```

That may give you some clues as to what went wrong.

You can also look for a file called `last-build.log` in the directory where your Terraform
configuration resides.

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

- **Maven repository access** - the build process must get various dependencies from a remote Maven
  respository; if your laptop cannot reach Maven Central, is configured to get dependencies from
  some other Maven repository, etc - you might need to fix this issue. You can check your
  `~/.m2/settings.xml` file, which might give you some insight into what Maven repository you're
  using. It's also where you'd configure credentials for a private Maven repository, such as
  Artifactory/etc - so make sure those are correct.

### Upgrading Psoxy Code

If you upgrade your psoxy code, it may be worth trying `terraform init --upgrade` to make sure you
have the latest versions of all Terraform providers on which our configuration depends.

By default, terraform locks providers to the version that was the latest when you first ran
`terraform init`. It does not upgrade them unless you explicitly instruct it to. It will not prompt
you to upgrade them unless we update the version constraints in our modules.

While we strive to ensure accurate version constraints, and use provider features consistent with
these constraints, our automated tests will run with the _latest_ version of each provider.
Regretably, we don't currently have a way to test with ALL versions of each provider that satisfy
the constraints, or all possible combinations of provider versions.

### State Inconsistencies

Often, in response to errors, a second run of `terraform apply` will work.

If something was _actually_ created in the cloud provider, but Terraform state doesn't reflect it,
then try `terraform import [resource] [provider-resource-id]`. `[resource]` should be replaced with
whatever the path to it is in your terraform configuration, which you can get from the
`terraform plan` output. `provider-resource-id` is a little trickier, and you might need to find the
format required by finding the Terraform docs for the resource type on the web.

NOTE: resources in plan with brackets/quotes will need these escaped with a backslash for use in
bash commands.

eg

```shell
terraform import module.psoxy-msft-connector\[\"outlook-cal\"\].aws_lambda_function_url.lambda_url psoxy-outlook-cal
```
