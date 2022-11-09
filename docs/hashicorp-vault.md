# Using HashiCorp Vault with Psoxy

As of Nov 10, 2022, Psoxy has added *alpha* support for using Hashicorp Vault as its secret store
(rather than AWS Systems Manager Parameter Store or GCP Secret Manager). We're releasing this as an
*alpha* feature, with potential for breaking changes to be introduced in any future release,
including minor releases which should not break production-ready features.

# Getting Started with HashiCorp Vault

  1. Set the following environment variables in your instance:

     * `VAULT_ADDR` - the address of your Vault instance, e.g. `https://vault.example.com:8200`
       * NOTE: must be accessible from AWS account / GCP project where you're deploying
     * `VAULT_TOKEN` - choose the appropriate token type for your use case; if it's going to expire,
        it's your responsibility either renew it OR replace it by updating this environment variable
        before expiration.
     * `VAULT_NAMESPACE` - optional, if you're using Vault Namespaces
     * `PATH_TO_SHARED_CONFIG` - eg, `secret/worklytics_deployment/shared/`
     * `PATH_TO_CONNECTOR_CONFIG` - eg, `secret/worklytics_deployment/gcal/`

  2. **Configure your secrets in Vault.** Given the above, Psoxy will connect to Vault in lieu of
     the usual Secret storage solution for your cloud provider. It will expect config properties
     (secrets) organized as follows:
        * global secrets: `${PATH_TO_SHARED_CONFIG}${PROPERTY_NAME}`, eg with vault path
         `worklytics_deployment`, then:
            * `worklytics_deployment/PSOXY_SALT`
            * `worklytics_deployment/PSOXY_ENCRYPTION_KEY`
        * per-connector secrets:`${PATH_TO_CONNECTOR_CONFIG}${PROPERTY_NAME}` eg with vault path
          `worklytics_deployment` and function id `PSOXY_GCAL`
            * `worklytics_deployment/PSOXY_GCAL_RULES`
            * `worklytics_deployment/PSOXY_GCAL_ACCESS_TOKEN`
            * `worklytics_deployment/PSOXY_GCAL_CLIENT_ID`
            * `worklytics_deployment/PSOXY_GCAL_CLIENT_SECRET`

   3. **Ensure ACL permits 'read' and, if necessary, write**. Psoxy will need to be able to read
      secrets from Vault, and in some cases (eg, Oauth tokens subject to refresh) write.
