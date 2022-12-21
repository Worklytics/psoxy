# DEPRECATED; use `worklytics-psoxy-connection` with psoxy_host_platform_id==AWS

# alternatively, could keep this as a 'strong' interface for AWS-case; but you end up with lots of
# permutations (GCP, AWS) x (REST, BULK), etc.

module "generic" {
  source = "../worklytics-psoxy-connection"

  psoxy_instance_id      = var.psoxy_instance_id
  psoxy_host_platform_id = "AWS"
  psoxy_endpoint_url     = var.psoxy_endpoint_url
  todo_step              = var.todo_step
  display_name           = var.display_name
  settings_to_provide = {
    "AWS Psoxy Role ARN" = var.aws_role_arn,
    "AWS Psoxy Region"   = var.aws_region
  }
}

output "next_todo_step" {
  value = module.generic.next_todo_step
}
