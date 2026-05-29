# stores secrets as AWS SSM Parameters
# NOTE: value of this module is a consistent interface across potential Secret store implementations
#   eg, GCP Secret Manager, AWS SSM Parameter Store, Hashicorp Vault, etc.
#  but is this good Terraform style? clearly in AWS case, this module doesn't do much ...

locals {
  # AWS SSM param name must be fully qualified if contains `/`;
  # so test for that case, and prefix with `/` if needed
  non_empty_path           = length(var.path) > 0
  non_fully_qualified_path = length(regexall("/", var.path)) > 0 && !startswith(var.path, "/")
  path_prefix              = local.non_empty_path && local.non_fully_qualified_path ? "/${var.path}" : var.path
  PLACEHOLDER_VALUE        = "fill me"

  secret_keys                    = nonsensitive(toset(keys(var.secrets)))
  externally_managed_secret_keys = toset([for k in local.secret_keys : k if !nonsensitive(var.secrets[k].value_managed_by_tf)])
  terraform_managed_secret_keys  = toset([for k in local.secret_keys : k if nonsensitive(var.secrets[k].value_managed_by_tf)])
}

# see: https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ssm_parameter
resource "aws_ssm_parameter" "secret" {
  for_each = local.terraform_managed_secret_keys

  name = "${local.path_prefix}${each.key}"
  # Due https://github.com/hashicorp/terraform-provider-aws/issues/31267
  # all are added as secureString
  type        = "SecureString"
  description = var.secrets[each.key].description
  value       = sensitive(coalesce(var.secrets[each.key].value, local.PLACEHOLDER_VALUE))
  key_id      = coalesce(var.kms_key_id, "alias/aws/ssm")

  lifecycle {
    ignore_changes = [
      # value, # previously, we ignored changes to value; but this doesn't actually prevent new
      # value from being in your state file - so little point.
      tags
    ]
  }
}

resource "aws_ssm_parameter" "secret_with_externally_managed_value" {
  for_each = local.externally_managed_secret_keys

  name = "${local.path_prefix}${each.key}"
  # Due https://github.com/hashicorp/terraform-provider-aws/issues/31267
  # all are added as secureString
  type        = "SecureString"
  description = var.secrets[each.key].description
  value       = sensitive(coalesce(var.secrets[each.key].value, local.PLACEHOLDER_VALUE))
  key_id      = coalesce(var.kms_key_id, "alias/aws/ssm")

  lifecycle {
    ignore_changes = [
      value, # key difference here; we don't want to overwrite values filled by the external process
      tags
    ]
  }
}

# for use in explicit IAM policy grants?
# q: good idea? breaks notion of AWS SSM parameters secrets being an implementation of a generic
# secrets-store interface
# q: is to ALSO pass in some notion of access? except very different per implementation
output "secret_ids" {
  value = merge(
    { for k in local.terraform_managed_secret_keys : k => aws_ssm_parameter.secret[k].id },
    { for k in local.externally_managed_secret_keys : k => aws_ssm_parameter.secret_with_externally_managed_value[k].id }
  )
}

output "secret_arns" {
  value = concat(
    [for k in local.terraform_managed_secret_keys : aws_ssm_parameter.secret[k].arn],
    [for k in local.externally_managed_secret_keys : aws_ssm_parameter.secret_with_externally_managed_value[k].arn]
  )
}
