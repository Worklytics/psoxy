# provisions infra for a federated credential on a Microsoft Data Source connector (in Azure AD)

terraform {
  required_providers {
    azuread = {
      version = ">= 2.14.0, < 3.0.0"
    }
  }
}

# introduced in 2.14.0 - https://registry.terraform.io/providers/hashicorp/azuread/2.14.0/docs/resources/application_federated_identity_credential
resource "azuread_application_federated_identity_credential" "federated_credential" {
  # Deprecated since 2.44.0: https://registry.terraform.io/providers/hashicorp/azuread/2.44.0/docs/resources/application_federated_identity_credential
  application_object_id = var.application_object_id
  # Uncomment when azuread version is bumped to 2.44.0 or greater
  #application_id        = var.application_object_id
  display_name = var.display_name
  description  = var.description
  audiences    = [var.audience]
  issuer       = var.issuer
  subject      = var.subject
}

output "credential" {
  value = azuread_application_federated_identity_credential.federated_credential
}

output "audience" {
  value = var.audience
}
