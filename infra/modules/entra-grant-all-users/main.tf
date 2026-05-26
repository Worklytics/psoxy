# makes grant on behalf of ALL users in your Microsoft Entra ID tenant
#  - there is no way to do another org unit / group via Terraform; if that's the configure you
#   desire, you'll have to do that via Microsoft Entra admin center or CLI

# TODO: if grant can be made fully through API, do it here; until then, TODO file is best option

# NOTE: using `azuread_service_principal_delegated_permission_grant` seems to NOT work for this,
# presumably it ONLY supports oauth scopes (delegated permissions) not application roles
# (application permissions)
# however, even using it JUST for delegated permissions seems to create an inconsistency when user
# manually grants the application permissions (resource seems to really create a 'delegated_permission_grant'
# entity in Azure, which is overwritten by the user and then missing on subsequent terraform runs)

locals {
  instance_id  = coalesce(var.psoxy_instance_id, var.application_name)
  todo_content = <<EOT
# Authorize ${var.application_name}

Visit the following page in the Microsoft Entra admin center and grant the required application permissions:

https://entra.microsoft.com/#view/Microsoft_AAD_RegisteredApps/ApplicationMenuBlade/~/ApiPermissions/appId/${var.application_id}/isMSAApp~/false

If you are not a sufficiently privileged Microsoft Entra ID Administrator (likely Application or Global
Administrator), you may need assistance from an appropriately privileged member of your IT team.

The required grants are:
```
${join("\n", concat(var.app_roles, var.oauth2_permission_scopes))}
```

EOT
}

resource "local_file" "todo" {
  count = var.todos_as_local_files ? 1 : 0

  filename = "TODO ${var.todo_step} - setup ${local.instance_id}.md"

  content = local.todo_content
}

output "todo" {
  value = local.todo_content
}

output "filename" {
  value = var.todos_as_local_files ? local_file.todo[0].filename : null
}

output "next_todo_step" {
  value = var.todo_step + 1
}