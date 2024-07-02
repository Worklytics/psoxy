# provisions infra for a Microsoft Data Source connector (in Azure AD)
#  - the connector application
#  - granting connector access on behalf of the users in your Azure AD directory

terraform {
  required_providers {
    azuread = {
      version = ">= 2.7.0"
    }
  }
}

locals {
  provision_azuread_application = var.existing_app_object_id == null
}

data "azuread_application_published_app_ids" "well_known" {
  count = local.provision_azuread_application ? 1 : 0
}

data "azuread_service_principal" "msgraph" {
  count = local.provision_azuread_application ? 1 : 0

  # Deprecated since 2.44.0:
  # https://registry.terraform.io/providers/hashicorp/azuread/2.44.0/docs/data-sources/service_principal
  application_id = data.azuread_application_published_app_ids.well_known[0].result.MicrosoftGraph
  # Uncomment when azuread version is bumped to 2.44.0 or greater
  # client_id      = data.azuread_application_published_app_ids.well_known.result.MicrosoftGraph
}

data "azuread_application" "connector" {
  count = local.provision_azuread_application ? 0 : 1

  object_id = var.existing_app_object_id
}

resource "azuread_application" "connector" {
  count = local.provision_azuread_application ? 1 : 0

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
    resource_app_id = data.azuread_application_published_app_ids.well_known[0].result.MicrosoftGraph

    dynamic "resource_access" {
      for_each = var.required_oauth2_permission_scopes

      content {
        # this approach is consistent with what you get via `az ad sp list`, which is what MSFT docs
        # recommend: https://docs.microsoft.com/en-us/graph/permissions-reference#retrieving-permission-ids
        id   = data.azuread_service_principal.msgraph[0].oauth2_permission_scope_ids[resource_access.value]
        type = "Scope"
      }
    }

    dynamic "resource_access" {
      for_each = var.required_app_roles

      content {
        id   = data.azuread_service_principal.msgraph[0].app_role_ids[resource_access.value]
        type = "Role"
      }
    }
  }
}

output "connector" {
  value = local.provision_azuread_application ? azuread_application.connector[0] : data.azuread_application.connector[0]
}
