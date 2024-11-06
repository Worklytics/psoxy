# creates a Bulk processing instance of Psoxy, with existing S3 bucket as the input
# TODO: highly duplicative with regular `aws-psoxy-bulk` case, and could likely be unified in future
# version

module "psoxy_lambda" {
  source = "../aws-psoxy-lambda"

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
  statement_id  = "AllowExecutionFromS3Bucket"
  action        = "lambda:InvokeFunction"
  function_name = module.psoxy_lambda.function_name
  principal     = "s3.amazonaws.com"
  source_arn    = data.aws_s3_bucket.input.arn
}

resource "aws_s3_bucket_notification" "bucket_notification" {

  bucket = var.input_bucket

  lambda_function {
    lambda_function_arn = module.psoxy_lambda.function_arn
    events              = ["s3:ObjectCreated:*"]
  }

  depends_on = [aws_lambda_permission.allow_input_bucket]
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
  source = "../aws-psoxy-output-bucket"

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
