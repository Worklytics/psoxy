terraform {
  required_providers {
    # for the infra that will host Psoxy instances
    aws = {
      source  = "hashicorp/aws"
      version = "~> 3.0"
    }
  }

  # if you leave this as local, you should backup/commit your TF state files
  backend "local" {
  }
}

# NOTE: you need to provide credentials. usual way to do this is to set env vars:
#        AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
# see https://registry.terraform.io/providers/hashicorp/aws/latest/docs#authentication for more
# information as well as alternative auth approaches
provider "aws" {
  region = var.aws_region

  assume_role {
    role_arn = var.aws_assume_role_arn
  }
  allowed_account_ids = [
    var.aws_account_id
  ]
}

module "psoxy-aws" {
  source = "git::https://github.com/worklytics/psoxy//infra/modules/aws"

  caller_aws_account_id   = var.caller_aws_account_id
  caller_external_user_id = var.caller_external_user_id
  aws_account_id          = var.aws_account_id
}

module "psoxy-package" {
  source = "git::https://github.com/worklytics/psoxy//infra/modules/psoxy-package"

  implementation     = "aws"
  path_to_psoxy_java = "../../../java"
}

locals {

}

resource "aws_s3_bucket" "input" {
  bucket = "${var.bucket_prefix}-input"
}

resource "aws_s3_bucket" "output" {
  bucket = "${var.bucket_prefix}-output"

}

module "psoxy-file-handler" {
  source = "git::https://github.com/worklytics/psoxy//infra/modules/aws-psoxy-instance"

  function_name        = "psoxy-hris"
  handler_class        = "co.worklytics.psoxy.S3Handler"
  source_kind          = "hris"
  api_gateway          = module.psoxy-aws.api_gateway
  path_to_function_zip = module.psoxy-package.path_to_deployment_jar
  function_zip_hash    = module.psoxy-package.deployment_package_hash
  path_to_config       = "../../../configs/hris.yaml"
  api_caller_role_arn  = module.psoxy-aws.api_caller_role_arn
  aws_assume_role_arn  = var.aws_assume_role_arn
  example_api_calls    = [] #None, as this function is called through the S3 event

  parameters = []

  environment_variables = {
    INPUT_BUCKET  = aws_s3_bucket.input.bucket,
    OUTPUT_BUCKET = aws_s3_bucket.output.bucket
  }

  depends_on = [
    aws_s3_bucket.input,
    aws_s3_bucket.output
  ]
}

resource "aws_lambda_permission" "allow_input_bucket" {
  statement_id  = "AllowExecutionFromS3Bucket"
  action        = "lambda:InvokeFunction"
  function_name = module.psoxy-file-handler.function_arn
  principal     = "s3.amazonaws.com"
  source_arn    = aws_s3_bucket.input.arn

  depends_on = [
    aws_s3_bucket.input
  ]
}


resource "aws_s3_bucket_notification" "bucket_notification" {
  bucket = aws_s3_bucket.input.id

  lambda_function {
    lambda_function_arn = module.psoxy-file-handler.function_arn
    events              = ["s3:ObjectCreated:*"]
  }

  depends_on = [aws_lambda_permission.allow_input_bucket]
}

resource "aws_iam_policy" "input_bucket_read_policy" {
  name        = "BucketRead_${aws_s3_bucket.input.id}"
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

  depends_on = [
    aws_s3_bucket.input,
  ]
}

resource "aws_iam_role_policy_attachment" "read_policy_for_import_bucket" {
  role       = module.psoxy-file-handler.iam_for_lambda_name
  policy_arn = aws_iam_policy.input_bucket_read_policy.arn
}

resource "aws_iam_policy" "output_bucket_write_policy" {
  name        = "BucketWrite_${aws_s3_bucket.output.id}"
  description = "Allow principal to write to bucket: ${aws_s3_bucket.output.id}"

  policy = jsonencode(
    {
      "Version" : "2012-10-17",
      "Statement" : [
        {
          "Action" : [
            "s3:PutObject",
          ],
          "Effect" : "Allow",
          "Resource" : "${aws_s3_bucket.output.arn}/*"
        }
      ]
  })

  depends_on = [
    aws_s3_bucket.output
  ]
}

resource "aws_iam_role_policy_attachment" "write_policy_for_output_bucket" {
  role       = module.psoxy-file-handler.iam_for_lambda_name
  policy_arn = aws_iam_policy.output_bucket_write_policy.arn
}

resource "aws_iam_policy" "output_bucket_read" {
  name        = "BucketRead_${aws_s3_bucket.output.id}"
  description = "Allow to read content from bucket: ${aws_s3_bucket.output.id}"

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
            "${aws_s3_bucket.output.arn}",
            "${aws_s3_bucket.output.arn}/*"
          ]
        }
      ]
  })

  depends_on = [
    aws_s3_bucket.output
  ]
}

resource "aws_iam_role_policy_attachment" "caller_bucket_access_policy" {
  role       = module.psoxy-aws.api_caller_role_name
  policy_arn = aws_iam_policy.output_bucket_read.arn
}
