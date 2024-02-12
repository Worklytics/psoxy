# stores secrets as AWS Secret Manager Secrets
# NOTE: value of this module is a consistent interface across potential Secret store implementations
#   eg, GCP Secret Manager, AWS SSM Parameter Store, Hashicorp Vault, AWS Secrets Manager, etc.
#  but is this good Terraform style? clearly in AWS case, this module doesn't do much ...

locals {
  # AWS SSM param name must be fully qualified if contains `/`;
  # so test for that case, and prefix with `/` if needed
  non_empty_path           = length(var.path) > 0
  non_fully_qualified_path = length(regexall("/", var.path)) > 0 && !startswith(var.path, "/")
  path_prefix              = local.non_empty_path && local.non_fully_qualified_path ? "/${var.path}" : var.path
  PLACEHOLDER_VALUE        = "fill me"

  # externally_managed_secrets = { for k, spec in var.secrets : k => spec if !(spec.value_managed_by_tf) }
  terraform_managed_secrets = { for k, spec in var.secrets : k => spec if spec.value_managed_by_tf }

  tf_management_description_appendix = "Value managed by a Terraform configuration; changes outside Terraform may be overwritten by subsequent 'terraform apply' runs"
}

# see: https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ssm_parameter
resource "aws_secretsmanager_secret" "secret" {
  for_each = var.secrets

  name        = "${local.path_prefix}${each.key}"
  description = "${each.value.description} ${each.value.value_managed_by_tf ? local.tf_management_description_appendix : ""}"
  kms_key_id  = var.kms_key_id
}

resource "aws_secretsmanager_secret_version" "terraform_managed" {
  for_each = local.terraform_managed_secrets

  secret_id     = aws_secretsmanager_secret.secret[each.key].id
  secret_string = each.value.value
}

output "secret_ids" {
  value = { for k, v in aws_secretsmanager_secret.secret : k => aws_secretsmanager_secret.secret[k].id }
}

# actually the same as id
output "secret_arns" {
  value = { for k, v in aws_secretsmanager_secret.secret : k => aws_secretsmanager_secret.secret[k].arn }
}
