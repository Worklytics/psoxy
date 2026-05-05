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

# NOTE: local_file resource was moved to root module. todos_as_local_files/todo_step are no-ops here.
# TODO: remove deprecated variables/outputs in 0.7

output "todo" {
  value       = local.todo_content
  description = "[DEPRECATED - use todo_content output instead. TODO: remove in 0.7]"
}

output "filename" {
  value = null
  description = "[DEPRECATED - local_file resources moved to root module. TODO: remove in 0.7]"
}

output "next_todo_step" {
  value       = var.todo_step + 1
  description = "[DEPRECATED - todo ordering now handled at root module level via todo_content stage indices. TODO: remove in 0.7]"
}

output "todo_content" {
  description = "Structured todo content to be written to local files by root module. List of stages; each stage is a list of {name, content, file_permission} objects."
  value = [[
    {
      name            = "setup ${local.instance_id}"
      content         = local.todo_content
      file_permission = null
    }
  ]]
}