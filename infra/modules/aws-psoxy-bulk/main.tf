resource "random_string" "bucket_suffix" {
  length  = 8
  lower   = true
  upper   = false
  special = false
}

module "psoxy_lambda" {
  source = "../aws-psoxy-lambda"

  function_name                   = "psoxy-${var.instance_id}"
  handler_class                   = "co.worklytics.psoxy.S3Handler"
  timeout_seconds                 = 600 # 10 minutes
  memory_size_mb                  = var.memory_size_mb
  source_kind                     = var.source_kind
  path_to_function_zip            = var.path_to_function_zip
  function_zip_hash               = var.function_zip_hash
  global_parameter_arns           = var.global_parameter_arns
  path_to_instance_ssm_parameters = var.path_to_instance_ssm_parameters
  ssm_kms_key_ids                 = var.ssm_kms_key_ids

  environment_variables = merge(
    var.environment_variables,
    {
      INPUT_BUCKET  = aws_s3_bucket.input.bucket,
      OUTPUT_BUCKET = aws_s3_bucket.sanitized.bucket,
    }
  )
}

resource "aws_s3_bucket" "input" {
  bucket = "psoxy-${var.instance_id}-${random_string.bucket_suffix.id}-input"

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "input" {
  bucket = aws_s3_bucket.input.bucket

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "aws:kms"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "input-block-public-access" {
  bucket = aws_s3_bucket.input.bucket

  block_public_acls       = true
  block_public_policy     = true
  restrict_public_buckets = true
  ignore_public_acls      = true
}

resource "aws_s3_bucket" "sanitized" {
  bucket = "psoxy-${var.instance_id}-${random_string.bucket_suffix.id}-sanitized"

  lifecycle {
    ignore_changes = [
      bucket, # due to rename
      tags
    ]
  }
}

moved {
  from = aws_s3_bucket.output
  to   = aws_s3_bucket.sanitized
}

resource "aws_s3_bucket_server_side_encryption_configuration" "sanitized" {
  bucket = aws_s3_bucket.sanitized.bucket

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "aws:kms"
    }
  }
}

moved {
  from = aws_s3_bucket_server_side_encryption_configuration.output
  to   = aws_s3_bucket_server_side_encryption_configuration.sanitized
}

resource "aws_s3_bucket_public_access_block" "sanitized" {
  bucket = aws_s3_bucket.sanitized.bucket

  block_public_acls       = true
  block_public_policy     = true
  restrict_public_buckets = true
  ignore_public_acls      = true
}

moved {
  from = aws_s3_bucket_public_access_block.output-block-public-access
  to   = aws_s3_bucket_public_access_block.sanitized
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
  name        = "BucketGetObject_${aws_s3_bucket.input.id}"
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
  name        = "BucketWrite_${aws_s3_bucket.sanitized.id}"
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

moved {
  from = aws_iam_policy.output_bucket_write_policy
  to   = aws_iam_policy.sanitized_bucket_write_policy
}

resource "aws_iam_role_policy_attachment" "write_policy_for_sanitized_bucket" {
  role       = module.psoxy_lambda.iam_role_for_lambda_name
  policy_arn = aws_iam_policy.sanitized_bucket_write_policy.arn
}

moved {
  from = aws_iam_role_policy_attachment.write_policy_for_output_bucket
  to   = aws_iam_role_policy_attachment.write_policy_for_sanitized_bucket
}

# proxy caller (data consumer) needs to read (both get and list objects) from the output bucket
resource "aws_iam_policy" "sanitized_bucket_read" {
  name        = "BucketRead_${aws_s3_bucket.sanitized.id}"
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

moved {
  from = aws_iam_policy.output_bucket_read
  to   = aws_iam_policy.sanitized_bucket_read
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
  name           = "PSOXY_${upper(replace(var.instance_id, "-", "_"))}_RULES"
  type           = "String"
  description    = "Rules for transformation of files. NOTE: any 'RULES' env var will override this value"
  insecure_value = yamlencode(var.rules) # NOTE: insecure_value just means shown in Terraform output

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}


resource "local_file" "todo-aws-psoxy-bulk-test" {
  filename = "TODO ${var.todo_step} - test ${var.instance_id}.md"
  content  = <<EOT

## Testing Psoxy Bulk: ${var.instance_id}

Review the deployed function in AWS console:

- https://console.aws.amazon.com/lambda/home?region=${var.aws_region}#/functions/${module.psoxy_lambda.function_name}?tab=monitoring

We provide some Node.js scripts to easily validate the deployment. To be able to run the test
commands below, you need Node.js (>=16) and npm (v >=8) installed. Ensure all dependencies are
installed by running:

```shell
${local.command_npm_install}
```

Then, check that the Psoxy works as expected and it transforms the files of your input bucket
following the rules you have defined. Change the value of the `-f` option in the following command
with the path of a CSV file (*) you would like to test:

```shell
node ${var.psoxy_base_dir}tools/psoxy-test/cli-file-upload.js -f ${local.example_file} -d AWS -i ${aws_s3_bucket.input.bucket} -o ${aws_s3_bucket.sanitized.bucket} -r ${var.aws_assume_role_arn} -re ${var.aws_region}
```

Notice that the rest of the options should match your Psoxy configuration.

(*) Check supported formats in [Bulk Data Imports Docs](https://app.worklytics.co/docs/hris-import)

---

Please, check the documentation of our [Psoxy Testing tools](${var.psoxy_base_dir}tools/psoxy-test/README.md)
for a detailed description of all the different options.

EOT
}

resource "local_file" "test_script" {
  filename        = "test-${var.instance_id}.sh"
  file_permission = "0770"
  content         = <<EOT
#!/bin/bash
FILE_PATH=$${1:-${try(local.example_file, "")}}
BLUE='\e[0;34m'
NC='\e[0m'

printf "Quick test of $${BLUE}${var.instance_id}$${NC} ...\n"

node ${var.psoxy_base_dir}tools/psoxy-test/cli-file-upload.js -f $FILE_PATH -d AWS -i ${aws_s3_bucket.input.bucket} -o ${aws_s3_bucket.sanitized.bucket} -r ${var.aws_assume_role_arn} -re ${var.aws_region}
EOT

}

# to facilitate composition of ingestion pipeline
output "input_bucket" {
  value = aws_s3_bucket.input.bucket
}

# to facilitate composition of output pipeline
output "sanitized_bucket" {
  value = aws_s3_bucket.sanitized.bucket
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

output "instance_id" {
  value = module.psoxy_lambda.function_name
}

output "proxy_kind" {
  value       = "bulk"
  description = "The kind of proxy instance this is."
}

output "next_todo_step" {
  value = var.todo_step + 1
}

