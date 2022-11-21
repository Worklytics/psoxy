# provisions infra for a federated credential on a Microsoft Data Source connector (in Azure AD)

terraform {
  required_providers {
    azuread = {
      version = "~> 2.15.0"
    }
  }
}

resource "azuread_application_federated_identity_credential" "federated_credential" {
  application_object_id = var.application_object_id
  display_name          = var.display_name
  description           = var.description
  audiences             = ["api://AzureADTokenExchange"]
  issuer                = var.issuer
  subject               = var.subject
}

output "credential" {
  value = azuread_application_federated_identity_credential.federated_credential
}