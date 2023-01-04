# provisions vault policy needed by psoxy instance's role, independent of auth method (token, aws-iam, etc)
# ALPHA support - YMMV

locals {
  instance_id_upper_snakecase = upper(replace(var.instance_id, "-", "_"))
  path_to_instance_secrets    = coalesce(var.path_to_instance_secrets, "secret/${local.instance_id_upper_snakecase}/")
}

# NOTE: as of 4 Jan 2023, this policy seems to only work if modified to allow read of `secret/*`

resource "vault_policy" "psoxy_instance" {
  name   = var.instance_id
  policy = <<EOT
path "${var.path_to_global_secrets}*"
{
	capabilities = ["read"]
}

path "${local.path_to_instance_secrets}*"
{
    capabilities = ["create", "read", "update"]
}
EOT
}


output "vault_policy_name" {
  value = vault_policy.psoxy_instance.name
}
