locals {
  non_empty_path           = length(var.ssm_path) > 0
  non_fully_qualified_path = length(regexall("/", var.ssm_path)) > 0 && !startswith(var.ssm_path, "/")
  path_prefix              = local.non_empty_path && local.non_fully_qualified_path ? "/${var.ssm_path}" : var.ssm_path

  path_prefix_for_lambda = "${local.path_prefix}PSOXY_${upper(replace(var.instance_id, "-", "_"))}"
}

resource "aws_ssm_parameter" "private-key" {
  name        = "${local.path_prefix_for_lambda}_PRIVATE_KEY"
  type        = "SecureString"
  description = "Value of private key"
  value       = var.private_key
  key_id      = coalesce(var.kms_key_id, "alias/aws/ssm")


  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

resource "aws_ssm_parameter" "private-key-id" {
  name        = "${local.path_prefix_for_lambda}_PRIVATE_KEY_ID"
  type        = "SecureString" # probably not necessary
  description = "ID of private key"
  value       = var.private_key_id
  key_id      = coalesce(var.kms_key_id, "alias/aws/ssm")

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

output "parameters" {
  value = [
    aws_ssm_parameter.private-key-id,
    aws_ssm_parameter.private-key
  ]
}
