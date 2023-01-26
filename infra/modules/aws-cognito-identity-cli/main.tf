provider "aws" {
  assume_role {
    role_arn = var.aws_role
  }
}

resource "null_resource" "identity-command" {
  for_each = var.login-ids

  triggers = {
    always_run = "${timestamp()}"
  }
  provisioner "local-exec" {
    command = "bash ${path.module}/addIdentity.sh ${var.identity_pool_id} ${each.value} ${var.aws_region} ${var.aws_role} ${each.key} ${path.module}"
  }
}

data "local_file" "identity-result" {
  for_each = var.login-ids

  filename   = "${path.module}/cognito_identity_${each.key}.json"
  depends_on = [null_resource.identity-command]
}

output "identity_id" {
  value = { for k, v in var.login-ids : k => jsondecode(data.local_file.identity-result[k].content).IdentityId }
}