# makes grant on behalf of ALL users in your Azure AD directory
#  - there is no way to do another org unit / group via Terraform; if that's the configure you
#   desire, you'll have to do that via Azure AD console OR cli


data "azuread_application_published_app_ids" "well_known" {}

resource "azuread_service_principal" "msgraph" {
  application_id = data.azuread_application_published_app_ids.well_known.result.MicrosoftGraph
  use_existing   = true
}

resource "azuread_service_principal" "connector" {
  application_id = var.application_id
}

resource "azuread_service_principal_delegated_permission_grant" "example" {
  service_principal_object_id          = azuread_service_principal.connector.object_id
  resource_service_principal_object_id = azuread_service_principal.msgraph.object_id
  claim_values                         = [for permission in var.required_resource_access : permission.id ]
}
