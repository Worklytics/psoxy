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