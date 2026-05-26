# Using AWS Secrets Manager

By default, Psoxy uses AWS Systems Manager Parameter Store to store secrets; this simplifies
configuration and minimizes costs. However, you may want to use AWS Secrets Manager to store secrets
due to organization policy.

Add the following to your `terraform.tfvars`:

```hcl
secrets_store_implementation = "aws_secrets_manager"
```

This alters the behavior of the Terraform modules so everything considered a **secret** is
stored/loaded from AWS Secrets Manager instead of Parameter Store. Parameter Store is still used for
non-secret configuration (proxy rules, etc.).

Changes are also made to AWS IAM policies to allow Lambda execution roles to access Secrets Manager
as needed.

If any secrets are managed outside of Terraform (such as API keys for certain connectors), grant
access to the relevant secrets in Secrets Manager to the principals that will manage them.

## Configuration

Secrets are created only in the region where your proxy infrastructure is deployed, based on the
value set in your `terraform.tfvars`.

By default, secret names are prefixed with your environment ID. To override this, set
`aws_secrets_manager_path` in `terraform.tfvars`:

```hcl
aws_secrets_manager_path = "/your/custom/path"
```

Secret paths/names follow the same conventions as SSM parameters (eg, under `aws_ssm_param_root_path`
when applicable).

## Migration from Parameter Store

The default storage for secrets is AWS Systems Manager Parameter Store SecureString parameters. If
you have existing secrets in Parameter Store that _aren't_ managed by Terraform, copy them to a
secure location before migrating so you do not need to re-create values for every source.

## Setup

1. Ensure your Terraform configuration supports `secrets_store_implementation` (available since
   `v0.4.47`).

2. Add `secrets_store_implementation = "aws_secrets_manager"` to your `terraform.tfvars`.

3. If you previously filled secret values via the AWS web console (such as API secrets referenced in
   `TODO 1` files for certain sources), copy those values now.

4. Run `terraform apply`; review the plan and confirm when ready.

5. Fill secret values in Secrets Manager that you copied from Parameter Store. If you did not copy
   the values, see the `TODO 1..` files for each connector to obtain new values.

## Filling Secret Values via AWS Console

Navigate to the AWS Secrets Manager console and find the secret you need to fill. If there is not an
option to fill the value, click 'Retrieve secret value'; it should then prompt you with an option
to fill it.

IMPORTANT: Choose 'Plain Text' and remove the brackets (`{}`) that AWS prefills the input with!

Then copy-paste the value EXACTLY as-is. Ensure no leading/trailing whitespace or newlines, and no
encoding problems.

## Notes

- Some connectors, in particular Zoom and Jira, rotate tokens frequently and may generate many
  versions of secrets. AWS typically bills per secret rather than per version, with only one version
  staged as 'current', but you should monitor usage and review the pricing model of your AWS
  contract.
- Secrets Manager secrets are stored and accessed using the same path conventions as SSM parameters
  (eg, at the value of `aws_ssm_param_root_path`, if any).
