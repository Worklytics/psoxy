provider "aws" {
  assume_role {
    role_arn    = var.aws_role
  }
}

data "external" "identity-command" {
  for_each = var.login-ids
  program = [
    "bash",
    "${path.module}/addIdentity.sh",
    var.identity_pool_id,
    each.value.login,
    var.aws_region,
    var.aws_role
  ]
}

output "identity_id" {
  #value = data.external.identity-command.result.IdentityId
value = { for k, v in var.login-ids : k => data.external.identity-command[k].result.IdentityId }
}