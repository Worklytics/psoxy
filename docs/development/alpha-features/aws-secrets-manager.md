# AWS Secrets Manager Support

With `v0.4.47`, we're adding **alpha** support for AWS Secrets Manager. This feature is not yet
fully documented or stable.

A couple notes:

- some connectors, in particular Zoom/Jira, rotate tokens frequently so will generate a lot of
  versions of secrets. AFAIK, AWS will still bill you for just one secret, as only one should be
  staged as the 'current' version. But you should monitor this and review the particular terms and
  pricing model of your AWS contract.
- our modules will create secrets ONLY in the region into which your proxy infra is being deployed,
  based on the value set in your `terraform.tfvars` file.

Migration from Parameter Store: the default storage for secrets is as AWS Systems Manager Parameter
Store SecureString parameters. If you have existing secrets in Parameter Store that _aren't_ managed
by terraform, you can copy them to a secure location to avoid needing to re-create them for every
source.

## Setup

1. If you forked the `psoxy-example-aws` repo prior to `v0.4.47`, you should copy a `main.tf` and
   `variables.tf` from that version of later of the repo and unify the version numbers with your
   own. (>=0.4.47)

2. Add the following to your `terraform.tfvars`:

```hcl
secrets_store_implementation="aws_secrets_manager"
```

3. If you previously filled any secret values via AWS web console (such as API secrets you were
   directed to create in `TODO 1` files for certain sources, you should copy those values now).

4. `terraform apply`; review plan and confirm when ready

5. Fill values of secrets in Secrets Manager that you copied from Parameter Store. If you did not
   copy the values, see the `TODO 1..` files for each connector to obtain new values.

## Filling Secret Manager Secret values via AWS Console

Navigate to the AWS Secrets Manager console and find the secret you need to fill. If there's not an
option to fill the value, click 'Retrieve secret value'; it should then prompt you with option to
fill it.

IMPORTANT: Choose 'Plain Text' and remove the brackets (`{}`) that AWS prefills the input with!

Then copy-paste the value EXACTLY as-is. Ensure no leading/trailing whitespace or newlines, and no
encoding problems.

## NOTES

- AWS Secret Manager secrets will be stored/accessed with same path as SSM parameters. Eg, at value
  of `aws_ssm_param_root_path`, if any.

## Future work

q: support distinct path for secrets? or generalize parameter naming?
