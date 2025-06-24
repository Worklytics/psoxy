# Using AWS Secrets Manager

By default, Psoxy uses AWS Systems Manager Parameter Store to store secrets; this simplifies configuration and minimizes costs. However, you may want to use AWS Secrets Manager to store secrets due to organization policy.

In such a case, you can add the following to your `terraform.tfvars` file:

```hcl
secrets_store_implementation = "aws-secrets-manager"
```

This will alter the behavior of the Terraform modules to store everything considered a **secret** to be stored/loaded from AWS Secrets Manager instead of AWS Systems Manager Parameter Store. Note that Parameter Store is still used for non-secret configuration information, such as proxy rules, etc.

Changes will also be made to AWS IAM Policies, to allow lambda function execution roles to access Secrets Manager as needed.

If any secrets are managed outside of Terraform (such as API keys for certain connectors), you will need to grant access to relevant secrets in Secrets Manager to the principals that will manage these.
