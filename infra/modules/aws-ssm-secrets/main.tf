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
}

# see: https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ssm_parameter
resource "aws_ssm_parameter" "secret" {
  for_each = var.secrets

  name        = "${local.path_prefix}${each.key}"
  type        = "SecureString"
  description = each.value.description
  value       = sensitive(each.value.value)
  key_id      = coalesce(var.kms_key_id, "alias/aws/ssm")

  lifecycle {
    ignore_changes = [
      # value, # previously, we ignored changes to value; but this doesn't actually prevent new
      # value from being in your state file - so little point.
      tags
    ]
  }
}

# for use in explicit IAM policy grants?
# q: good idea? breaks notion of AWS SSM parameters secrets being an implementation of a generic
# secrets-store interface
# q: is to ALSO pass in some notion of access? except very different per implementation
output "secret_ids" {
  value = { for k, v in var.secrets : k => aws_ssm_parameter.secret[k].id }
}

output "secret_arns" {
  value = [for k, v in var.secrets : aws_ssm_parameter.secret[k].arn]
}
