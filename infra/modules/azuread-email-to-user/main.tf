terraform {
  required_providers {
    azuread = {
      version = "~> 2.15.0"
    }
  }
}

data "external" "user" {
  for_each = var.emails

  program = [
    "az ad user show --id ${each.value}"
  ]
}

output "users" {
  value = { for k, v in var.emails : k => data.external.user[k].result.id }
}