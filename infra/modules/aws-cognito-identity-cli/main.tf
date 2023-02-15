data "external" "identity-result" {
  for_each = var.login_ids

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
  value = { for k, v in var.login_ids : k => data.external.identity-result[k].result.IdentityId }
}
