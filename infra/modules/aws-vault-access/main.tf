# provisions role, bound to AWS ARN, for a psoxy instance
# ALPHA support - YMMV

resource "vault_aws_auth_backend_role" "example" {
  backend                  = var.aws_auth_backend_name
  role                     = var.instance_id
  auth_type                = "iam"
  bound_iam_principal_arns = [var.role_arn]
  resolve_aws_unique_ids   = false
  token_ttl                = 3600      # in seconds; so this is one hour
  token_max_ttl            = 24 * 3600 # in seconds; so this is one day
  token_policies           = [
    var.vault_policy_name
  ]
}

