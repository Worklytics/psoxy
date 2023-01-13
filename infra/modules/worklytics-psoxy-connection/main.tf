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
  settings_to_provide = merge(var.settings_to_provide,
    {
      "Psoxy Base URL" = var.psoxy_endpoint_url
    }
  )
}
