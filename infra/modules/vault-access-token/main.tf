# module to setup vault access for a psoxy instance
# ALPHA support - YMMV

# NOTE: this is a sensitive value that will be written to your Terraform state. For production use,
# you MUST handle your terraform state with equivalent security to the vault secrets that this would
# allow access to. We recommend that you DO NOT persist the state to your local file system or
# commit it to a source code repository.

# a Periodic Token to be used by the proxy instance
# see : https://developer.hashicorp.com/vault/tutorials/tokens/tokens#periodic-service-tokens

resource "vault_token_auth_backend_role" "lambda_role" {
  role_name        = var.instance_id
  allowed_policies = [var.vault_policy_name]
  orphan           = true
  token_period     = 14 * 24 * 60 * 60 # 2 weeks
  renewable        = true

  # no explicit_max_ttl, so should be a 'periodic' token

}

resource "vault_token" "periodic_service_token" {
  role_name       = vault_token_auth_backend_role.lambda_role.role_name # name to give token's role in Vault
  period          = "${14 * 24}h"                                       # 2 weeks
  renewable       = true
  renew_min_lease = 8 * 24 * 60 * 60  # 8 days
  renew_increment = 14 * 24 * 60 * 60 # 2 weeks
  policies        = [var.vault_policy_name]
  # no explicit_max_ttl, so should be a 'periodic' token
}


# actual vault token to output, so can be passed as env var to proxy instance
output "vault_token" {
  value = vault_token.periodic_service_token.client_token
}
