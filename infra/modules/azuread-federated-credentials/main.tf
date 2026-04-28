# provisions infra for a federated credential on a Microsoft Data Source connector (in Microsoft Entra ID)
#
# NOTE: this module is named 'azuread-*' because it uses the HashiCorp 'azuread' Terraform provider;
# the provider itself retains its original name as a convention and to avoid breaking changes for
# provider users, so we follow that convention in this module name.

terraform {
  required_providers {
    azuread = {
      version = ">= 2.44.0, < 4.0.0"
    }
  }
}

resource "azuread_application_federated_identity_credential" "federated_credential" {
  application_id = var.application_id
  display_name   = var.display_name
  description    = var.description
  audiences      = [var.audience]
  issuer         = var.issuer
  subject        = var.subject
}

output "credential" {
  value = azuread_application_federated_identity_credential.federated_credential
}

output "audience" {
  value = var.audience
}
