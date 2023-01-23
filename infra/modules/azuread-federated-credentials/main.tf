# provisions infra for a federated credential on a Microsoft Data Source connector (in Azure AD)

terraform {
  required_providers {
    azuread = {
      version = "~> 2.15.0"
    }
  }
}

locals {
  # This is the recommended value from MSFT, as it is what AAD expects to be in the "aud"
  # property token value
  # See https://learn.microsoft.com/en-us/azure/active-directory/develop/workload-identity-federation-create-trust?pivots=identity-wif-apps-methods-azp#important-considerations-and-restrictions
  audience = "api://AzureADTokenExchange"
}

resource "azuread_application_federated_identity_credential" "federated_credential" {
  application_object_id = var.application_object_id
  display_name          = var.display_name
  description           = var.description
  audiences             = [local.audience]
  issuer                = var.issuer
  subject               = var.subject
}

output "credential" {
  value = azuread_application_federated_identity_credential.federated_credential
}

output "audience" {
  value = local.audience
}