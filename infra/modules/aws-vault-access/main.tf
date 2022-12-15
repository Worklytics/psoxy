# provisions policy + role, bound to AWS ARN, for a psoxy instance
# ALPHA support - YMMV

locals {
  instance_id_upper_snakecase = upper(replace(var.instance_id, "-", "_"))
  path_to_instance_secrets    = coalesce(var.path_to_instance_secrets, "secret/${local.instance_id_upper_snakecase}")
}

resource "vault_policy" "psoxy_instance" {
  name   = var.instance_id
  policy = <<EOT
path "${var.path_to_global_secrets}"
{
	capabilities = ["read"]
}

path "${local.path_to_instance_secrets}/*"
{
    capabilities = ["create", "read", "update"]
}
EOT
}

resource "vault_aws_auth_backend_role" "example" {
  backend                  = var.aws_auth_backend_name
  role                     = var.instance_id
  auth_type                = "iam"
  bound_iam_principal_arns = [var.role_arn]
  resolve_aws_unique_ids   = false
  token_ttl                = 3600      # in seconds; so this is one hour
  token_max_ttl            = 24 * 3600 # in seconds; so this is one day
  token_policies           = [vault_policy.psoxy_instance.name]
}
