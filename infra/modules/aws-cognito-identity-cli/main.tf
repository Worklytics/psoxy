data "external" "identity-command" {
  program = [
    "${path.module}/addIdentity.sh",
    var.identity_pool_id,
    var.login,
    var.aws_region
  ]
}

output "identity_id" {
  value = data.external.identity-command.result.IdentityId
}