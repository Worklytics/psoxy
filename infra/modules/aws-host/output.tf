
output "path_to_deployment_jar" {
  description = "Path to the package to deploy (JAR)."
  value       = module.psoxy.path_to_deployment_jar
}

output "caller_role_name" {
  value = module.psoxy.api_caller_role_name
}

output "api_connector_instances" {
  value = local.api_instances
}

output "bulk_connector_instances" {
  value = local.bulk_instances
}

output "todos" {
  description = "List of todo steps to complete, in markdown format."
  value       = values(module.api_connector)[*].todo
}

output "next_todo_step" {
  value = max(concat(
    values(module.api_connector)[*].next_todo_step,
    values(module.bulk_connector)[*].next_todo_step
  )...)
}
