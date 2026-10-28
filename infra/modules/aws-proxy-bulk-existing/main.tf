# creates a Bulk processing instance of Psoxy, with existing S3 bucket as the input
# TODO: highly duplicative with regular `aws-proxy-bulk` case, and could likely be unified in future
# version

module "env_id" {
  source = "../env-id"

  environment_name          = var.environment_name
  supported_word_delimiters = ["-"]
  preferred_word_delimiter  = "-"
}

locals {
  use_eventbridge_trigger = var.file_pattern != null && trimspace(var.file_pattern) != ""
  eventbridge_rule_name   = substr("${module.env_id.id}-${replace(var.instance_id, "_", "-")}-bulk-s3", 0, 64)
}

check "file_pattern_exclusive_with_prefix_suffix" {
  assert {
    condition     = !local.use_eventbridge_trigger || (var.file_prefix == null && var.file_suffix == null)
    error_message = "Set either file_pattern (EventBridge trigger) or file_prefix/file_suffix (native S3 notification trigger), not both."
  }
}

module "psoxy_lambda" {
  source = "../aws-proxy-lambda"

  environment_name                     = var.environment_name
  instance_id                          = var.instance_id
  handler_class                        = "co.worklytics.psoxy.S3Handler"
  timeout_seconds                      = 600 # 10 minutes
  memory_size_mb                       = var.memory_size_mb
  path_to_function_zip                 = var.path_to_function_zip
  function_zip_hash                    = var.function_zip_hash
  global_parameter_arns                = var.global_parameter_arns
  global_secrets_manager_secrets_arns  = var.global_secrets_manager_secret_arns
  path_to_instance_ssm_parameters      = var.path_to_instance_ssm_parameters
  path_to_shared_ssm_parameters        = var.path_to_shared_ssm_parameters
  function_env_kms_key_arn             = var.function_env_kms_key_arn
  logs_kms_key_arn                     = var.logs_kms_key_arn
  ssm_kms_key_ids                      = var.ssm_kms_key_ids
  vpc_config                           = var.vpc_config
  secrets_store_implementation         = var.secrets_store_implementation
  aws_lambda_execution_role_policy_arn = var.aws_lambda_execution_role_policy_arn
  iam_roles_permissions_boundary       = var.iam_roles_permissions_boundary


  environment_variables = merge(
    var.environment_variables,
    {
      INPUT_BUCKET  = var.input_bucket
      OUTPUT_BUCKET = module.sanitized_output_bucket.output_bucket
    }
  )
}

data "aws_s3_bucket" "input" {
  bucket = var.input_bucket
}

resource "aws_lambda_permission" "allow_input_bucket" {
  count = local.use_eventbridge_trigger ? 0 : 1

  statement_id  = "AllowExecutionFromS3Bucket"
  action        = "lambda:InvokeFunction"
  function_name = module.psoxy_lambda.function_name
  # TODO(v0.8): use function ARN (provider `function_name` accepts ARN) and remove `depends_on` below
  # function_name = module.psoxy_lambda.function_arn
  principal     = "s3.amazonaws.com"
  source_arn    = data.aws_s3_bucket.input.arn

  depends_on = [module.psoxy_lambda] # TODO(v0.8): remove once invoke permissions use function_arn
}

resource "aws_lambda_permission" "allow_eventbridge" {
  count = local.use_eventbridge_trigger ? 1 : 0

  statement_id  = "AllowExecutionFromEventBridge"
  action        = "lambda:InvokeFunction"
  function_name = module.psoxy_lambda.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.bulk_s3_object_created[0].arn

  depends_on = [module.psoxy_lambda]
}

resource "aws_cloudwatch_event_rule" "bulk_s3_object_created" {
  count = local.use_eventbridge_trigger ? 1 : 0

  name        = local.eventbridge_rule_name
  description = "Invoke bulk Psoxy Lambda for S3 Object Created events matching file_pattern (${var.instance_id})"
  event_pattern = jsonencode({
    source      = ["aws.s3"]
    detail-type = ["Object Created"]
    detail = {
      bucket = {
        name = [data.aws_s3_bucket.input.bucket]
      }
      object = {
        key = [{ wildcard = var.file_pattern }]
      }
    }
  })
}

resource "aws_cloudwatch_event_target" "bulk_s3_lambda" {
  count = local.use_eventbridge_trigger ? 1 : 0

  rule      = aws_cloudwatch_event_rule.bulk_s3_object_created[0].name
  target_id = "bulk-lambda"
  arn       = module.psoxy_lambda.function_arn
}

resource "aws_s3_bucket_notification" "bucket_notification" {

  bucket = var.input_bucket

  eventbridge = local.use_eventbridge_trigger

  dynamic "lambda_function" {
    for_each = local.use_eventbridge_trigger ? [] : [1]
    content {
      lambda_function_arn = module.psoxy_lambda.function_arn
      events              = ["s3:ObjectCreated:*"]
      filter_prefix       = var.file_prefix != null ? var.file_prefix : ""
      filter_suffix       = var.file_suffix != null ? var.file_suffix : ""
    }
  }

  depends_on = [module.psoxy_lambda, aws_lambda_permission.allow_input_bucket, aws_lambda_permission.allow_eventbridge]
}


# the lambda function needs to get single objects from the input bucket
resource "aws_iam_policy" "input_bucket_getObject_policy" {
  name        = "${data.aws_s3_bucket.input.id}_BucketGetObject_${module.psoxy_lambda.function_name}"
  description = "Allow principal to read from input bucket: ${data.aws_s3_bucket.input.id}"

  policy = jsonencode(
    {
      "Version" : "2012-10-17",
      "Statement" : [
        {
          "Action" : [
            "s3:GetObject"
          ],
          "Effect" : "Allow",
          "Resource" : "${data.aws_s3_bucket.input.arn}/*"
        }
      ]
  })

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

resource "aws_iam_role_policy_attachment" "read_policy_for_import_bucket" {
  role       = module.psoxy_lambda.iam_role_for_lambda_name
  policy_arn = aws_iam_policy.input_bucket_getObject_policy.arn
}

module "sanitized_output_bucket" {
  source = "../aws-proxy-output-bucket"

  environment_name                     = var.environment_name
  instance_id                          = var.instance_id
  iam_role_for_lambda_name             = module.psoxy_lambda.iam_role_for_lambda_name
  sanitized_accessor_role_names        = var.sanitized_accessor_role_names
  provision_bucket_public_access_block = var.provision_bucket_public_access_block
}


resource "aws_ssm_parameter" "rules" {
  name           = "${var.path_to_instance_ssm_parameters}RULES"
  type           = "String"
  description    = "Rules for transformation of files. NOTE: any 'RULES' env var will override this value"
  insecure_value = yamlencode(var.rules) # NOTE: insecure_value just means shown in Terraform output


  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

# to facilitate composition of output pipeline
output "output_bucket" {
  value = module.sanitized_output_bucket.output_bucket
}

output "function_arn" {
  value = module.psoxy_lambda.function_arn
}

output "instance_role_arn" {
  value = module.psoxy_lambda.iam_role_for_lambda_arn
}

output "instance_id" {
  value = module.psoxy_lambda.function_name
}

output "next_todo_step" {
  value = var.todo_step + 1
}
