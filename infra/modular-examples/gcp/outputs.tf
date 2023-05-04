output "path_to_deployment_jar" {
  description = "Path to the package to deploy (JAR) as lambda."
  value       = module.psoxy.path_to_deployment_jar
}

output "instances" {
  description = "Instances of Psoxy connectors deployments as Cloud Functions."
  value       = local.all_instances
}

output "todos_1" {
  description = "List of todo steps to complete 1st, in markdown format."
  value = concat(
    values(module.google_workspace_connection)[*].todo,
    values(module.source_token_external_todo)[*].todo,
  )
}

output "todos_2" {
  description = "List of todo steps to complete 2nd, in markdown format."
  value = concat(
    values(module.psoxy_google_workspace_connector)[*].todo,
    values(module.connector_long_auth_function)[*].todo,
    values(module.psoxy_bulk)[*].todo,
  )
}

output "todos_3" {
  description = "List of todo steps to complete 3rd, in markdown format."
  value = concat(
    values(module.worklytics_psoxy_connection)[*].todo,
    values(module.worklytics_psoxy_connection)[*].todo,
    values(module.psoxy_bulk_to_worklytics)[*].todo,
  )
}

# use case: let someone consume this deploy another psoxy instance, reusing artifacts
output "artifacts_bucket_name" {
  description = "Name of GCS bucket with deployment artifacts."
  value       = module.psoxy.artifacts_bucket_name
}

output "deployment_bundle_object_name" {
  description = "Object name of deployment bundle within artifacts bucket."
  value       = module.psoxy.deployment_bundle_object_name
}
