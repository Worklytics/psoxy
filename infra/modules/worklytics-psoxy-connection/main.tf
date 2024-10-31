# NOTE: coupled to REST use case; use `worklytics-psoxy-connection-generic` to cover REST or bulk
# cases.

# DEPRECATED: use generic one directly

module "generic" {
  source = "../worklytics-psoxy-connection-generic"

  psoxy_instance_id      = var.psoxy_instance_id
  connector_id           = var.connector_id
  psoxy_host_platform_id = var.psoxy_host_platform_id
  display_name           = var.display_name
  todo_step              = var.todo_step
  todos_as_local_files   = var.todos_as_local_files
  worklytics_host        = var.worklytics_host

  settings_to_provide = merge(var.settings_to_provide,
    {
      "Psoxy Base URL" = var.psoxy_endpoint_url
    }
  )
}

output "todo" {
  value = module.generic.todo
}

output "next_todo_step" {
  value = module.generic.next_todo_step
}

output "tenant_api_connection_settings" {
  value = module.generic.tenant_api_connection_settings
}
