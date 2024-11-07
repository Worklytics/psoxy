output "path_to_deployment_jar" {
  description = "Path to the package to deploy (JAR) as lambda."
  value       = module.psoxy_aws.path_to_deployment_jar
}

output "deployment_package_hash" {
  description = "Hash of deployment package."
  value       = module.psoxy_aws.deployment_package_hash
}

output "instances" {
  value = local.all_instances
}

output "lookup_tables" {
  value = { for k, v in var.lookup_table_builders : k => module.lookup_output[k].output_bucket }
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
    values(module.aws_psoxy_long_auth_connectors)[*].todo,
  )
}

output "todos_3" {
  description = "List of todo steps to complete 3rd, in markdown format."
  value = concat(
    values(module.worklytics_psoxy_connection_google_workspace)[*].todo,
    values(module.psoxy_bulk_to_worklytics)[*].todo,
    values(module.worklytics_psoxy_connection)[*].todo
  )
}

output "caller_role_arn" {
  description = "ARN of the AWS IAM role that can be assumed to invoke the Lambdas."
  value       = module.psoxy_aws.api_caller_role_arn
}

output "tenant_api_connection_settings" {
  value = concat(
    values(module.worklytics_psoxy_connection)[*].tenant_api_settings,
    values(module.worklytics_psoxy_connection_google_workspace)[*].tenant_api_settings,
    values(module.psoxy_bulk_to_worklytics)[*].tenant_api_settings
  )
}
