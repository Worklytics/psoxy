# Using HashiCorp Vault with Psoxy _alpha_

As of Nov 10, 2022, Psoxy has added _alpha_ support for using Hashicorp Vault as its secret store
(rather than AWS Systems Manager Parameter Store or GCP Secret Manager). We're releasing this as an
_alpha_ feature, with potential for breaking changes to be introduced in any future release,
including minor releases which should not break production-ready features.

# Getting Started with HashiCorp Vault

NOTE: you will NOT be able to use the Terraform examples found in `infra/examples`; you will have to
adapt the modular forms of those found in `infra/modular-examples`, swapping the host platform's
secret manager for Vault.

1. Set the following environment variables in your instance:

   - `VAULT_ADDR` - the address of your Vault instance, e.g. `https://vault.example.com:8200`
     - NOTE: must be accessible from AWS account / GCP project where you're deploying
   - `VAULT_TOKEN` - choose the appropriate token type for your use case; we recommend you use a
     periodic token that can lookup and renew itself, with period of > 8 days. With such a setup,
     Psoxy will look up and renew this token as needed. Otherwise, it's your responsibility either
     renew it OR replace it by updating this environment variable before expiration.
   - `VAULT_NAMESPACE` - optional, if you're using Vault Namespaces
   - `PATH_TO_SHARED_CONFIG` - eg, `secret/worklytics_deployment/PSOXY_SHARED/`
   - `PATH_TO_INSTANCE_CONFIG` - eg, `secret/worklytics_deployment/PSOXY_GCAL/`

2. **Configure your secrets in Vault.** Given the above, Psoxy will connect to Vault in lieu of the
   usual Secret storage solution for your cloud provider. It will expect config properties (secrets)
   organized as follows:

   - global secrets: `${PATH_TO_SHARED_CONFIG}${PROPERTY_NAME}`, eg with `PATH_TO_SHARED_CONFIG` -
     eg, `secret/worklytics_deployment/PSOXY_SHARED/`then:
     - `secret/worklytics_deployment/PSOXY_SHARED/PSOXY_SALT`
     - `secret/worklytics_deployment/PSOXY_SHARED/PSOXY_ENCRYPTION_KEY`
   - per-connector secrets:`${PATH_TO_CONNECTOR_CONFIG}${PROPERTY_NAME}` eg with
     `PATH_TO_INSTANCE_CONFIG` as `secret/worklytics_deployment/PSOXY_GCAL/`:
     - `secret/worklytics_deployment/PSOXY_GCAL/RULES`
     - `secret/worklytics_deployment/PSOXY_GCAL/ACCESS_TOKEN`
     - `secret/worklytics_deployment/PSOXY_GCAL/CLIENT_ID`
     - `secret/worklytics_deployment/PSOXY_GCAL/CLIENT_SECRET`

3. **Ensure ACL permits 'read' and, if necessary, write**. Psoxy will need to be able to read
   secrets from Vault, and in some cases (eg, Oauth tokens subject to refresh) write. Additionally,
   if you're using a periodic token as recommended, the token must be authorized to lookup and renew
   itself.

## AWS IAM Auth _alpha_

Generally, follow Vault's guide:
[https://developer.hashicorp.com/vault/docs/auth/aws](https://developer.hashicorp.com/vault/docs/auth/aws)

We also have a Terraform module you can try to set-up Vault for use from Psoxy:

- [infra/modules/aws-vault-auth](../../../infra/modules/aws-vault-auth)

And another Terraform module to add Vault access for each psoxy instance:

- [infra/modules/aws-vault-access](../../../infra/modules/aws-vault-access)

Manually, steps are roughly:

- Create
  [IAM policy needed by Vault](https://developer.hashicorp.com/vault/docs/auth/aws#recommended-vault-iam-policy)
  in your AWS account.
- Create IAM User for Vault in your AWS account.
- Enable `aws` auth method in your Vault instance. Set access key + secret for the vault user
  created above.
- Create a Vault policy to allow access to the necessary secrets in Vault.
- Bind a Vault role with same name as your lambda function with lambda's AWS exec role (once for
  each lambda)

```shell
vault write auth/aws/role/psoxy-gcal auth_type=iam policies={{YOUR_VAULT_POLICY}} max_ttl=500h bound_iam_principal_arn={{EXECUTION_ROLE_ARN}}
```

NOTE: pretty certain must be plain role arn, not assumed_role arn even though that's what vault sees

- eg `arn:aws:iam::{{YOUR_AWS_ACCOUNT_ID}}:role/PsoxyExec_psoxy-gcal` not
  `arn:aws:sts::{{YOUR_AWS_ACCOUNT_ID}}:assumed_role/PsoxyExec_psoxy-gcal/psoxy-gcal`

## GCP IAM Auth _alpha_

TODO
