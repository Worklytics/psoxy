# custom REST rules as SSM parameter

# as a module to get reusable
# - variable validation
# - length calculations

# pros:
#  - if small, human readable.  if large, can be gzipped
#  - can be reviewed/managed via AWS console
#  - rule changes readily visible in plan, not confused with source code changes
#  - bundle the same for all lambdas; speeds build/package, reduces disk/mem footprint needed for
#    deploy (concern if doing cloud shell)
#
# cons vs shipping with lambda's deployment as flat file:
#  - lambda won't see rule changes unless restarted
#  - another component to see/manage
#  - less analogous to GCP; haven't implemented gcp yet, but we're using secret manager
#  - need to worry about lambda's permissions to read the SSM param
#  - need to worry about terraforms permissions to write the SSM param (if we're not otherwise
#    writing SSMs, which as of Apr 2023 we are so not really concern)


locals {
  # size limits, in bytes
  ssm_advanced_size_limit = 8192
  ssm_standard_size_limit = 4096

  # read rules from file or use content directly
  rules_plain = var.content != null ? var.content : file(var.file_path)

  # compress if necessary; but otherwise leave plain so human readable
  use_compressed = length(local.rules_plain) > local.ssm_advanced_size_limit
  param_value    = local.use_compressed ? base64gzip(local.rules_plain) : local.rules_plain

  store_in_ssm = length(local.param_value) < local.ssm_advanced_size_limit

  remote_object_key = var.remote_resource_instance_path != null ? "${trimsuffix(var.remote_resource_instance_path, "/")}/rules.yaml" : null
}

resource "aws_ssm_parameter" "rules" {
  count = local.store_in_ssm ? 1 : 0

  name           = "${var.prefix}RULES"
  type           = "String"
  description    = var.description
  tier           = length(local.param_value) < local.ssm_standard_size_limit ? "Standard" : "Advanced"
  insecure_value = local.param_value

  lifecycle {
    ignore_changes = [tags]

    precondition {
      condition = local.store_in_ssm || (
        var.remote_resource_bucket != null && var.remote_resource_instance_path != null
      )
      error_message = "Rules exceed the SSM Parameter Store size limit (8192 bytes). Set enable_remote_resources = true on the host module so rules can be stored in the artifacts bucket, or reduce rule size."
    }
  }
}

resource "aws_s3_object" "rules" {
  count = local.store_in_ssm ? 0 : 1

  bucket  = var.remote_resource_bucket
  key     = local.remote_object_key
  content = local.rules_plain

  lifecycle {
    precondition {
      condition = local.store_in_ssm || (
        var.remote_resource_bucket != null && var.remote_resource_instance_path != null
      )
      error_message = "Rules exceed the SSM Parameter Store size limit (8192 bytes). Set enable_remote_resources = true on the host module so rules can be stored in the artifacts bucket, or reduce rule size."
    }
  }
}

output "rules_hash" {
  value = sha1(local.rules_plain)
}

output "stored_in_remote_bucket" {
  value = !local.store_in_ssm
}
