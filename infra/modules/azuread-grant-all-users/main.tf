# makes grant on behalf of ALL users in your Azure AD directory
#  - there is no way to do another org unit / group via Terraform; if that's the configure you
#   desire, you'll have to do that via Azure AD console OR cli

terraform {
  required_providers {
    azuread = {
      version = "~> 2.15.0"
    }
  }
}

# TODO: if grant can be made fully through API, do it here; until then, TODO file is best option

# NOTE: using `azuread_service_principal_delegated_permission_grant` seems to NOT work for this,
# presumably it ONLY supports oauth scopes (delegated permissions) not application roles
# (application permissions)
# however, even using it JUST for delegated permissions seems to create an inconsistency when user
# manually grants the application permissions (resource seems to really create a 'delegated_permission_grant'
# entity in Azure, which is overwritten by the user and then missing on subsequent terraform runs)

resource "local_file" "todo" {
  filename = "TODO ${var.application_name}.md"

  content = <<EOT
# Authorize ${var.application_name}

Visit the following page in the Azure AD console and grant the required application permissions:

https://portal.azure.com/#blade/Microsoft_AAD_RegisteredApps/ApplicationMenuBlade/CallAnAPI/appId/${var.application_id}/isMSAApp/

If you are not a sufficiently privileged Azure AD Administrator (likely Application or Global
Administrator), you made need assistance from an appropriately privileged member of your IT team.

The required grants are:
```
${join("\n", concat(var.app_roles, var.oauth2_permission_scopes))}
```

EOT
}
