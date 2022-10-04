# stores secrets as Hashicorp Vault Generic Secrets

# secret in vault
# NOTE: reads of secret disabled, so 1) no read perms required to apply this terraform module, and
# 2) drift detection disabled, so secret's value will NOT be overwritten if it's changed outside
resource "vault_generic_secret" "secret" {
  for_each = var.secrets

  path         = "secret/${each.key}"
  data_json    = sensitive(each.value)
  disable_read = true
}


# for use in explicit IAM policy grants?
# q: good idea? breaks notion of AWS SSM parameters secrets being an implementation of a generic
# secrets-store interface
# q: is to ALSO pass in some notion of access? except very different per implementation
output "secret_ids" {
  value = { for k, v in var.secrets : k => aws_ssm_parameter.secret[k].path }
}

