
# NOTE: region used to be passed in as a variable; put it MUST match the region in which the lambda
# is provisioned, and that's implicit in the provider - so we should just infer from the provider
data "aws_region" "current" {}

resource "random_string" "bucket_suffix" {
  length  = 8
  lower   = true
  upper   = false
  special = false

  lifecycle {
    # just NEVER recreate this random string; never what we're going to want to do, as will re-create the buckets
    ignore_changes = [
      length,
      special,
      lower,
      upper,
      numeric,
    ]
  }
}

module "env_id" {
  source = "../env-id"

  environment_name          = var.environment_name
  supported_word_delimiters = ["-"]
  preferred_word_delimiter  = "-"
}

locals {
  bucket_name_prefix = "${module.env_id.id}-${replace(var.instance_id, "_", "-")}"
  iam_policy_prefix  = "${module.env_id.id}-${replace(var.instance_id, " ", "_")}"
}


module "psoxy_lambda" {
  source = "../aws-psoxy-lambda"

  environment_name                     = var.environment_name
  instance_id                          = var.instance_id
  handler_class                        = "co.worklytics.psoxy.S3Handler"
  timeout_seconds                      = 600 # 10 minutes
  memory_size_mb                       = var.memory_size_mb
  ephemeral_storage_mb                 = 10240 # max it out; expected to cost nothing
  source_kind                          = var.source_kind
  path_to_function_zip                 = var.path_to_function_zip
  function_zip_hash                    = var.function_zip_hash
  function_env_kms_key_arn             = var.function_env_kms_key_arn
  logs_kms_key_arn                     = var.logs_kms_key_arn
  global_parameter_arns                = var.global_parameter_arns
  global_secrets_manager_secret_arns   = var.global_secrets_manager_secret_arns
  secrets_store_implementation         = var.secrets_store_implementation
  path_to_instance_ssm_parameters      = var.path_to_instance_ssm_parameters
  path_to_shared_ssm_parameters        = var.path_to_shared_ssm_parameters
  ssm_kms_key_ids                      = var.ssm_kms_key_ids
  log_retention_in_days                = var.log_retention_days
  vpc_config                           = var.vpc_config
  aws_lambda_execution_role_policy_arn = var.aws_lambda_execution_role_policy_arn
  iam_roles_permissions_boundary       = var.iam_roles_permissions_boundary



  environment_variables = merge(
    var.environment_variables,
    {
      INPUT_BUCKET  = aws_s3_bucket.input.bucket,
      OUTPUT_BUCKET = aws_s3_bucket.sanitized.bucket,
    }
  )
}

resource "aws_s3_bucket" "input" {
  bucket = "${local.bucket_name_prefix}-${random_string.bucket_suffix.id}-input"

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}


resource "aws_s3_bucket_public_access_block" "input-block-public-access" {
  count = var.provision_bucket_public_access_block ? 1 : 0

  bucket = aws_s3_bucket.input.bucket

  block_public_acls       = true
  block_public_policy     = true
  restrict_public_buckets = true
  ignore_public_acls      = true
}


resource "aws_s3_bucket_lifecycle_configuration" "expire_input_files" {
  bucket = aws_s3_bucket.input.bucket

  rule {
    id     = "expire"
    status = "Enabled"
    expiration {
      days = var.input_expiration_days
    }
  }
}

resource "aws_s3_bucket" "sanitized" {
  bucket = "${local.bucket_name_prefix}-${random_string.bucket_suffix.id}-sanitized"

  lifecycle {
    ignore_changes = [
      bucket, # due to rename
      tags
    ]
  }
}

resource "aws_s3_bucket_public_access_block" "sanitized" {
  count = var.provision_bucket_public_access_block ? 1 : 0

  bucket = aws_s3_bucket.sanitized.bucket

  block_public_acls       = true
  block_public_policy     = true
  restrict_public_buckets = true
  ignore_public_acls      = true
}

resource "aws_s3_bucket_lifecycle_configuration" "expire_sanitized_files" {
  bucket = aws_s3_bucket.sanitized.bucket

  rule {
    id     = "expire"
    status = "Enabled"
    expiration {
      days = var.sanitized_expiration_days
    }
  }
}

resource "aws_lambda_permission" "allow_input_bucket" {
  statement_id  = "AllowExecutionFromS3Bucket"
  action        = "lambda:InvokeFunction"
  function_name = module.psoxy_lambda.function_name
  principal     = "s3.amazonaws.com"
  source_arn    = aws_s3_bucket.input.arn
}

resource "aws_s3_bucket_notification" "bucket_notification" {
  bucket = aws_s3_bucket.input.id

  lambda_function {
    lambda_function_arn = module.psoxy_lambda.function_arn
    events              = ["s3:ObjectCreated:*"]
  }

  depends_on = [aws_lambda_permission.allow_input_bucket]
}

# the lambda function needs to get single objects from the input bucket
resource "aws_iam_policy" "input_bucket_getObject_policy" {
  name        = "${module.env_id.id}_BucketGetObject_${aws_s3_bucket.input.id}"
  description = "Allow principal to read from input bucket: ${aws_s3_bucket.input.id}"

  policy = jsonencode(
    {
      "Version" : "2012-10-17",
      "Statement" : [
        {
          "Action" : [
            "s3:GetObject"
          ],
          "Effect" : "Allow",
          "Resource" : "${aws_s3_bucket.input.arn}/*"
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

# proxy's lamba needs to WRITE to the output bucket
resource "aws_iam_policy" "sanitized_bucket_write_policy" {
  name        = "${module.env_id.id}_BucketWrite_${aws_s3_bucket.sanitized.id}"
  description = "Allow principal to write to bucket: ${aws_s3_bucket.sanitized.id}"

  policy = jsonencode(
    {
      "Version" : "2012-10-17",
      "Statement" : [
        {
          "Action" : [
            "s3:PutObject",
          ],
          "Effect" : "Allow",
          "Resource" : "${aws_s3_bucket.sanitized.arn}/*"
        }
      ]
  })

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

resource "aws_iam_role_policy_attachment" "write_policy_for_sanitized_bucket" {
  role       = module.psoxy_lambda.iam_role_for_lambda_name
  policy_arn = aws_iam_policy.sanitized_bucket_write_policy.arn
}

# proxy caller (data consumer) needs to read (both get and list objects) from the output bucket
resource "aws_iam_policy" "sanitized_bucket_read" {
  name        = "${module.env_id.id}_BucketRead_${aws_s3_bucket.sanitized.id}"
  description = "Allow to read content from bucket: ${aws_s3_bucket.sanitized.id}"

  policy = jsonencode(
    {
      "Version" : "2012-10-17",
      "Statement" : [
        {
          "Action" : [
            "s3:GetObject",
            "s3:ListBucket"
          ],
          "Effect" : "Allow",
          "Resource" : [
            "${aws_s3_bucket.sanitized.arn}",
            "${aws_s3_bucket.sanitized.arn}/*"
          ]
        }
      ]
  })

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}



locals {
  accessor_role_names = concat([var.api_caller_role_name], var.sanitized_accessor_role_names)
  command_npm_install = "npm --prefix ${var.psoxy_base_dir}tools/psoxy-test install"
  example_file        = var.example_file == null ? "/path/to/example.csv" : "${var.psoxy_base_dir}${var.example_file}"
}

resource "aws_iam_role_policy_attachment" "reader_policy_to_accessor_role" {
  for_each = toset([for r in local.accessor_role_names : r if r != null])

  role       = each.key
  policy_arn = aws_iam_policy.sanitized_bucket_read.arn
}

resource "aws_ssm_parameter" "rules" {
  name           = "${var.path_to_instance_ssm_parameters}RULES"
  type           = "String"
  description    = "Rules for transformation of files. NOTE: any 'RULES' env var will override this value"
  insecure_value = var.rules_file == null ? yamlencode(var.rules) : file(var.rules_file) # NOTE: insecure_value just means shown in Terraform output

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

resource "aws_iam_policy" "testing" {
  count = var.provision_iam_policy_for_testing ? 1 : 0

  name_prefix = "${local.iam_policy_prefix}Testing"
  description = "Allow to write to input bucket, read from sanitized bucket to test Lambda's behavior"
  policy = jsonencode({
    "Version" : "2012-10-17",
    "Statement" : [
      {
        "Action" : [
          "s3:PutObject"
        ]
        "Effect" : "Allow",
        "Resource" : [
          "${aws_s3_bucket.input.arn}",
          "${aws_s3_bucket.input.arn}/*"
        ]
      },
      {
        "Action" : [
          "s3:GetObject",
          "s3:ListBucket",
          "s3:DeleteObject",
          "s3:DeleteObjectVersion"
        ],
        "Effect" : "Allow",
        "Resource" : [
          "${aws_s3_bucket.sanitized.arn}",
          "${aws_s3_bucket.sanitized.arn}/*"
        ]
      }
    ]
  })
}



resource "aws_iam_policy_attachment" "testing_policy_to_testing_role" {
  count = var.provision_iam_policy_for_testing ? 1 : 0

  name       = "${aws_iam_policy.testing[count.index].name}_to_${var.instance_id}TestingRole"
  policy_arn = aws_iam_policy.testing[count.index].arn
  roles = [
    element(split("role/", var.aws_role_to_assume_when_testing), 1)
  ]
}

locals {
  role_option_for_tests = var.aws_role_to_assume_when_testing == null ? "" : "-r ${var.aws_role_to_assume_when_testing}"

  # id that is unique for connector, within the environment (eg, files with this token in name, but otherwise equivalent, will not conflict)
  local_file_id = trimprefix(var.instance_id, var.environment_name)

  # whether this connector needs set up
  need_setup = var.instructions_template != null

  test_todo_step = var.todo_step + (local.need_setup ? 1 : 0)
  setup_todo_content = var.instructions_template == null ? "" : templatefile(var.instructions_template, {
    input_bucket_url = "s3://${aws_s3_bucket.input.bucket}",
  })
  todo_brief = <<EOT
## Test ${var.instance_id}
Check that the Psoxy works as expected, and it transforms the files of your input bucket following
the rules you have defined:

```shell
node ${var.psoxy_base_dir}tools/psoxy-test/cli-file-upload.js -f ${local.example_file} ${local.role_option_for_tests} -d AWS -i ${aws_s3_bucket.input.bucket} -o ${aws_s3_bucket.sanitized.bucket} --region ${data.aws_region.current.id}
```
EOT

  test_todo_content = <<EOT
# Review Psoxy Bulk: ${var.instance_id}

Review the deployed function in AWS console:

- https://console.aws.amazon.com/lambda/home?region=${data.aws_region.current.id}#/functions/${module.psoxy_lambda.function_name}?tab=monitoring

We provide some Node.js scripts to easily validate the deployment. To be able to run the test
commands below, you need Node.js (>=16) and npm (v >=8) installed. Ensure all dependencies are
installed by running:

```shell
${local.command_npm_install}
```

${local.todo_brief}

Notice that the rest of the options passed as argument to the script should match your Psoxy
configuration.

(*) Check supported formats in [Bulk Data Imports Docs](https://app.worklytics.co/docs/hris-import)

---

Please, check the documentation of our [Psoxy Testing tools](${var.psoxy_base_dir}tools/psoxy-test/README.md)
for a detailed description of all the different options.

EOT
}

resource "local_file" "todo_setup" {
  count = (var.todos_as_local_files && local.need_setup) ? 1 : 0

  filename = "TODO ${var.todo_step} - setup ${local.local_file_id}.md"
  content  = local.setup_todo_content
}

resource "local_file" "todo_test" {
  count = var.todos_as_local_files ? 1 : 0

  filename = "TODO ${local.test_todo_step} - test ${var.instance_id}.md"
  content  = local.test_todo_content
}

locals {
  test_script = <<EOT
#!/bin/bash
FILE_PATH=$${1:-${try(local.example_file, "")}}
BLUE='\e[0;34m'
NC='\e[0m'

printf "Quick test of $${BLUE}${var.instance_id}$${NC} ...\n"

node ${var.psoxy_base_dir}tools/psoxy-test/cli-file-upload.js -f "$${FILE_PATH}" -d "AWS" -i "${aws_s3_bucket.input.bucket}" -o "${aws_s3_bucket.sanitized.bucket}" ${local.role_option_for_tests} --region "${var.aws_region}"
EOT
}

resource "local_file" "test_script" {
  count = var.todos_as_local_files ? 1 : 0

  filename        = "test-${local.local_file_id}.sh"
  file_permission = "755"
  content         = local.test_script
}

# to facilitate composition of ingestion pipeline
output "input_bucket" {
  value = aws_s3_bucket.input.bucket
}

# to facilitate composition of output pipeline
output "sanitized_bucket" {
  value = aws_s3_bucket.sanitized.bucket
}

output "example_files" {
  value = try(var.example_file, null) != null ? [{
    path           = var.example_file
    content_base64 = base64encode(file(local.example_file))
  }] : []
  description = "Array of example files with path relative to terraform config root and base64-encoded content"
}

output "instance_role_arn" {
  value = module.psoxy_lambda.iam_role_for_lambda_arn
}

output "instance_role_name" {
  value = module.psoxy_lambda.iam_role_for_lambda_name
}

output "function_arn" {
  value = module.psoxy_lambda.function_arn
}

output "function_name" {
  value = module.psoxy_lambda.function_name
}

# DEPRECATED; remove in v0.5
# this ends up being qualified by environment/deployment id, to avoid collisions - so not useful
# for identifying 'instance' of connector
output "instance_id" {
  value = module.psoxy_lambda.function_name
}

output "proxy_kind" {
  value       = "bulk"
  description = "The kind of proxy instance this is."
}

output "test_script" {
  value = try(local_file.test_script[0].filename, null)
}

output "test_script_content" {
  value = local.test_script
}

output "todo" {
  value = local.todo_brief
}

output "todo_setup" {
  value = local.setup_todo_content
}

output "next_todo_step" {
  value = var.todo_step + 1
}

