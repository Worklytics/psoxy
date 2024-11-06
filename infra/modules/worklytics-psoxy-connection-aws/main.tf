# create a connection from Worklytics to a Psoxy instance hosted in AWS
#
# kept as a 'strong' interface for AWS-case; enforces that the caller must provide the AWS-specific
# settings (role ARN, region, etc.)

module "generic" {
  source = "../worklytics-psoxy-connection-generic"

  proxy_instance_id      = var.proxy_instance_id
  host_platform_id       = "AWS"
  todo_step              = var.todo_step
  display_name           = var.display_name
  worklytics_host        = var.worklytics_host
  todos_as_local_files   = var.todos_as_local_files
  connector_id           = var.connector_id


  settings_to_provide = merge(
    var.proxy_endpoint_url == null ? {} : { "Psoxy Base URL" = var.proxy_endpoint_url },
    # Source Bucket (bulk file) case
    var.bucket_name == null ? {} : { "Bucket Name" = var.bucket_name },
    {
      "AWS Psoxy Role ARN" = var.aws_role_arn,
      "AWS Psoxy Region"   = var.aws_region,
    },
    var.connector_settings_to_provide
  )

}

output "next_todo_step" {
  value = module.generic.next_todo_step
}

output "todo" {
  value = module.generic.todo
}
