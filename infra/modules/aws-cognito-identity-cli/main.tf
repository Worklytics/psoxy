terraform {
  required_providers {
    external = {
      source  = "hashicorp/external"
      version = ">= 2.0"
    }
  }
}

data "external" "identity-result" {
  for_each = var.login_ids

  # NOTE: passing blank string will, in effect, shift arguments!!
  program = [
    "${path.module}/addIdentity.sh",
    var.identity_pool_id,
    each.value,
    var.aws_region,
    each.key,
    var.aws_role # possibly empty string, so must come last!!
  ]
}

output "identity_id" {
  value = { for k, v in var.login_ids : k => data.external.identity-result[k].result.IdentityId }
}
