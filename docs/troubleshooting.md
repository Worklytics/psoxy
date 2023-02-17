
# General Troubleshooting

## Specific Data Sources
 - [Microsoft 365](docs/sources/msft-365/troubleshooting.md)


## Specific Host platforms:
  - [AWS](docs/aws/troubleshooting.md)
  - [GCP](docs/gcp/troubleshooting.md)

## General Tips

### Build problems with Java 19 (specifically, openjdk 19.0.1)

If you are using openjdk 19.0.1, you may run into problems with the build. We suggest you downgrade
to some java 17 (which is Long-Term Support edition) and use that.

NOTE: we believe the issue is fixed in 19.0.2, which was [released Jan 23, 2023](https://www.oracle.com/java/technologies/javase/19-0-2-relnotes.html).
So alternatively you could try upgrading to that (or higher).

On Mac, steps would be:

1. check version
```bash
mvn -v
```
- java version says "17", you're good to go. otherwise, try to downgrade

2. install java 17
```bash
brew install openjdk@17
```

3. set `JAVA_HOME` env variable to point to java 17; for example:

```bash
export JAVA_HOME='/opt/homebrew/Cellar/openjdk@17/17.0.5/libexec/openjdk.jdk/Contents/Home'
```

or, possibly your Homebrew default installation is at `/usr/local/Cellar/`, in which case:

```bash
/usr/local/Cellar/openjdk@17/17.0.5/libexec/openjdk.jdk/Contents/Home
```

NOTE:
  - you may need to edit some similar `export` command in your `.bashrc`/`.zshrc` file, or similar;
    or repeat the above command every time you open a new terminal window.
  - if you install/upgrade something via Homebrew that depends on Java, you may need to repeat step
    3 again to reset your `JAVA_HOME`


### Upgrading Psoxy Code

If you upgrade your psoxy code, it may be worth trying `terraform init --upgrade` to make sure
you have the latest versions of all Terraform providers on which our configuration depends.

By default, terraform locks providers to the version that was the latest when you first ran
`terraform init`.  It does not upgrade them unless you explicitly instruct it to. It will not
prompt you to upgrade them unless we update the version constraints in our modules.

While we strive to ensure accurate version constraints, and use provider features consistent with
these constraints, our automated tests will run with the *latest* version of each provider.
Regretably, we don't currently have a way to test with ALL versions of each provider that satisfy the
constraints, or all possible combinations of provider versions.


### State Inconsistencies

Often, in response to errors, a second run of `terraform apply` will work.

If something was *actually* created in the cloud provider, but Terraform state doesn't reflect it,
then try `terraform import [resource] [provider-resource-id]`. `[resource]` should be replaced with
whatever the path to it is in your terraform configuration, which you can get from the
`terraform plan` output. `provider-resource-id` is a little trickier, and you might need to find
the format required by finding the Terraform docs for the resource type on the web.

NOTE: resources in plan with brackets/quotes will need these escaped with a backslash for use in
bash commands.

eg
```shell
terraform import module.psoxy-msft-connector\[\"outlook-cal\"\].aws_lambda_function_url.lambda_url psoxy-outlook-cal
```






