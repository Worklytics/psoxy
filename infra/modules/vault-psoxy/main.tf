# provisions vault policy needed by psoxy instance's role, independent of auth method (token, aws-iam, etc)
# ALPHA support - YMMV

locals {
  instance_id_upper_snakecase = upper(replace(var.instance_id, "-", "_"))
  path_to_instance_secrets    = coalesce(var.path_to_instance_secrets, "secret/${local.instance_id_upper_snakecase}/")
}

resource "vault_policy" "psoxy_instance" {
  name   = var.instance_id

  # TODO: as of 4 Jan 2023, secret/* case needed here; please scope better for your prod use!!
  policy = <<EOT
path "secret/*"
{
	capabilities = ["read"]
}
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
