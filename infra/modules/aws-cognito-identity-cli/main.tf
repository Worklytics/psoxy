#resource "null_resource" "identity-command" {
#  for_each = var.login-ids
#
#  triggers = {
#    always_run = "${timestamp()}"
#  }
#  provisioner "local-exec" {
#    command = "bash ${path.module}/addIdentity.sh ${var.identity_pool_id} ${each.value} ${var.aws_region} ${var.aws_role} ${each.key} ${path.module}"
#  }
#}
#
#data "local_file" "identity-result" {
#  for_each = var.login-ids
#
#  filename   = "${path.module}/cognito_identity_${each.key}.json"
#  depends_on = [null_resource.identity-command]
#}

data "external" "identity-result" {
  for_each = var.login-ids

  program = [
    "${path.module}/addIdentity.sh",
    var.identity_pool_id,
    each.value,
    var.aws_region,
    var.aws_role,
    each.key
  ]
}

output "identity_id" {
  value = { for k, v in var.login-ids : k => data.external.identity-result[k].result.IdentityId }
}