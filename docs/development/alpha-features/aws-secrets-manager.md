# AWS Secrets Manager Support

With `v0.4.47`, we're adding **alpha** support for AWS Secrets Manager. This feature is not yet
fully documented or stable.

Migration from Parameter Store: the default storage for secrets is as AWS Systems Manager Parameter
Store SecureString parameters. If you have existing secrets in Parameter Store, s

## Setup

  1. If you forked the `psoxy-example-aws` repo prior to `v0.4.47`, you should copy a `main.tf` and
`variables.tf` from that version of later of the repo and unify the version numbers with your own. (>=0.4.47)

  2. Add the following to your `terraform.tfvars`:
```hcl
secrets_store_implementation="aws_secrets_manager"
```
  3. If you previously filled any secret values via AWS web console (such as API secrets you were
     directed to create in `TODO 1` files for certain sources, you should copy those values now).

  4. `terraform apply`; review plan and confirm when ready

  5. Fill values of secrets in Secrets Manager that you copied from Parameter Store. If you did not
     copy the values, see the `TODO 1..` files for each connector to obtain new values.



## NOTES
  - AWS Secret Manager secrets will be stored/accessed with same path as SSM parameters. Eg, at value of
`aws_ssm_param_root_path`, if any.

## Future work
q: support distinct path for secrets? or generalize parameter naming?


