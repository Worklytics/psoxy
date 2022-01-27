# provisions infra for a Microsoft Data Source connector (in Azure AD)
#  - the connector application
#  - granting connector access on behalf of the users in your Azure AD directory

terraform {
  required_providers {
    azuread = {
      version = "~> 2.15.0"
    }
  }
}

data "azuread_application_published_app_ids" "well_known" {}

resource "azuread_service_principal" "msgraph" {
  application_id = data.azuread_application_published_app_ids.well_known.result.MicrosoftGraph
  use_existing   = true
}

resource "azuread_application" "connector" {
  display_name                   = var.display_name

  required_resource_access {
    resource_app_id = data.azuread_application_published_app_ids.well_known.result.MicrosoftGraph

    dynamic "resource_access" {
      for_each = var.required_oauth2_permission_scopes

      content {
        # this approach is consistent with what you get via `az ad sp list`, which is what MSFT docs
        # recommend: https://docs.microsoft.com/en-us/graph/permissions-reference#retrieving-permission-ids
        id   = azuread_service_principal.msgraph.oauth2_permission_scope_ids[resource_access.value]
        type = "Scope"
      }
    }

    dynamic "resource_access" {
      for_each = var.required_app_roles

      content {
        id   = azuread_service_principal.msgraph.app_role_ids[resource_access.value]
        type = "Role"
      }
    }
  }
}

output "connector" {
  value = azuread_application.connector
}
