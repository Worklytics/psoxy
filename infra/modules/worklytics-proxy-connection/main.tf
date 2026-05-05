# NOTE: coupled to REST use case; use `worklytics-proxy-connection-generic` to cover REST or bulk
# cases.

# DEPRECATED: use generic one directly

module "generic" {
  source = "../worklytics-proxy-connection-generic"

  proxy_instance_id    = var.proxy_instance_id
  connector_id         = var.connector_id
  host_platform_id     = var.host_platform_id
  display_name         = var.display_name
  todo_step            = var.todo_step
  todos_as_local_files = var.todos_as_local_files
  worklytics_host      = var.worklytics_host

  settings_to_provide = merge(var.settings_to_provide,
    {
      "Psoxy Base URL" = var.psoxy_endpoint_url
    }
  )
}

output "todo" {
  value       = module.generic.todo
  description = "[DEPRECATED - use todo_content output instead. TODO: remove in 0.7]"
}

output "next_todo_step" {
  value       = module.generic.next_todo_step
  description = "[DEPRECATED - todo ordering now handled at root module level via todo_content stage indices. TODO: remove in 0.7]"
}
