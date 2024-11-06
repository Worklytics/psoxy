# provisions infra for a Microsoft Data Source connector (in Azure AD)
#  - the connector application
#  - granting connector access on behalf of the users in your Azure AD directory

terraform {
  required_providers {
    azuread = {
      version = ">= 2.44.0, < 4.0.0"
    }
  }
}

data "azuread_application_published_app_ids" "well_known" {}

data "azuread_service_principal" "msgraph" {
  client_id = data.azuread_application_published_app_ids.well_known.result.MicrosoftGraph
}

resource "azuread_application" "connector" {
  display_name = var.display_name

  # NOTE: introduced in 2.7.0
  # see https://registry.terraform.io/providers/hashicorp/azuread/2.7.0/docs/resources/application
  feature_tags {
    hide       = true  # don't show as 'App' to users, as there is no user-facing experience for connector
    enterprise = false # default; just clarify this is intentional. see https://marileeturscak.medium.com/the-difference-between-app-registrations-enterprise-applications-and-service-principals-in-azure-4f70b9a80fe5
    # and internal discussion https://app.asana.com/0/1201039336231823/1202001336919865/f
    gallery = false # default; but to clarify intent
  }

  owners = var.owners

  required_resource_access {
    resource_app_id = data.azuread_application_published_app_ids.well_known.result.MicrosoftGraph

    dynamic "resource_access" {
      for_each = var.required_oauth2_permission_scopes

      content {
        # this approach is consistent with what you get via `az ad sp list`, which is what MSFT docs
        # recommend: https://docs.microsoft.com/en-us/graph/permissions-reference#retrieving-permission-ids
        id   = data.azuread_service_principal.msgraph.oauth2_permission_scope_ids[resource_access.value]
        type = "Scope"
      }
    }

    dynamic "resource_access" {
      for_each = var.required_app_roles

      content {
        id   = data.azuread_service_principal.msgraph.app_role_ids[resource_access.value]
        type = "Role"
      }
    }
  }

  lifecycle {
    # see https://registry.terraform.io/providers/hashicorp/azuread/latest/docs/resources/application#argument-reference
    ignore_changes = [
      # '(Optional) References application context information from a Service or Asset Management database.' --> have seen customers manage this outside our provided Terraform
      service_management_reference,
      # internal notes; have seen customers manage this outside our provided Terraform
      notes
    ]
  }
}

output "connector" {
  value = azuread_application.connector
}
