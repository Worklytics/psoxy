# provisions infra for a Microsoft Data Source connector (in Azure AD)
#  - the connector application
#  - granting connector access on behalf of the users in your Azure AD directory

data "azuread_application_published_app_ids" "well_known" {}

resource "azuread_service_principal" "msgraph" {
  application_id = data.azuread_application_published_app_ids.well_known.result.MicrosoftGraph
  use_existing   = true
}

resource "azuread_application" "connector" {
  display_name                   = var.display_name

  required_resource_access {
    resource_app_id =  data.azuread_application_published_app_ids.well_known.result.MicrosoftGraph

    dynamic "resource_access" {
      for_each = var.required_resources

      content {
        id   = azuread_service_principal.msgraph.oauth2_permission_scope_ids[resource_access.value.id]
        type = resource_access.value.type # generally, will be 'Role' for most use cases
      }
    }
  }
}

output "connector" {
  value = azuread_application.connector
}
