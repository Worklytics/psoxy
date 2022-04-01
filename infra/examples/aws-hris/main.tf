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
  source = "../../modules/aws"

  caller_aws_account_id   = var.caller_aws_account_id
  caller_external_user_id = var.caller_external_user_id
  aws_account_id          = var.aws_account_id

  providers = {
    aws = aws
  }
}

module "psoxy-package" {
  source = "../../modules/psoxy-package"

  implementation     = "aws"
  path_to_psoxy_java = "../../../java"
}

locals {

}

resource "aws_s3_bucket" "import_bucket" {
  bucket = "${var.bucket_prefix}-import"
}

resource "aws_s3_bucket" "processed_bucket" {
  bucket = "${var.bucket_prefix}-processed"
}

module "psoxy-file-handler" {
  source = "../../modules/aws-psoxy-instance"

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
    INPUT_BUCKET  = aws_s3_bucket.import_bucket.bucket,
    OUTPUT_BUCKET = aws_s3_bucket.processed_bucket.bucket
  }

  depends_on = [
    aws_s3_bucket.import_bucket,
    aws_s3_bucket.processed_bucket
  ]
}

resource "aws_lambda_permission" "allow_import_bucket" {
  statement_id  = "AllowExecutionFromS3Bucket"
  action        = "lambda:InvokeFunction"
  function_name = module.psoxy-file-handler.function_arn
  principal     = "s3.amazonaws.com"
  source_arn    = aws_s3_bucket.import_bucket.arn

  depends_on = [
    aws_s3_bucket.import_bucket
  ]
}


resource "aws_s3_bucket_notification" "bucket_notification" {
  bucket = aws_s3_bucket.import_bucket.id

  lambda_function {
    lambda_function_arn = module.psoxy-file-handler.function_arn
    events              = ["s3:ObjectCreated:*"]
  }

  depends_on = [aws_lambda_permission.allow_import_bucket]
}

resource "aws_iam_policy" "import_bucket_read_policy" {
  name        = "ReadFromImportBucket"
  description = "Allow lambda function role to read from import bucket"

  policy = jsonencode(
    {
      "Version" : "2012-10-17",
      "Statement" : [
        {
          "Action" : [
            "s3:GetObject"
          ],
          "Effect" : "Allow",
          "Resource" : "${aws_s3_bucket.import_bucket.arn}/*"
        }
      ]
  })

  depends_on = [
    aws_s3_bucket.import_bucket
  ]
}

resource "aws_iam_role_policy_attachment" "read_policy_for_import_bucket" {
  role       = module.psoxy-file-handler.iam_for_lambda_name
  policy_arn = aws_iam_policy.import_bucket_read_policy.arn
}

resource "aws_iam_policy" "processed_bucket_write_policy" {
  name        = "WriteForProcessedBucket"
  description = "Allow lambda function role to write to processed bucket"

  policy = jsonencode(
    {
      "Version" : "2012-10-17",
      "Statement" : [
        {
          "Action" : [
            "s3:PutObject",
          ],
          "Effect" : "Allow",
          "Resource" : "${aws_s3_bucket.processed_bucket.arn}/*"
        }
      ]
  })

  depends_on = [
    aws_s3_bucket.processed_bucket
  ]
}

resource "aws_iam_role_policy_attachment" "write_policy_for_processed_bucket" {
  role       = module.psoxy-file-handler.iam_for_lambda_name
  policy_arn = aws_iam_policy.processed_bucket_write_policy.arn
}

resource "aws_iam_policy" "worklyics_bucket_access_policy" {
  name        = "ReadAccessForWorklyics"
  description = "Allow Worklytics to access this bucket for reading its content"

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
            "${aws_s3_bucket.processed_bucket.arn}",
            "${aws_s3_bucket.processed_bucket.arn}/*"
          ]
        }
      ]
  })

  depends_on = [
    aws_s3_bucket.processed_bucket
  ]
}

resource "aws_iam_role_policy_attachment" "worklyics_bucket_access_policy" {
  role       = module.psoxy-aws.api_caller_role_name
  policy_arn = aws_iam_policy.worklyics_bucket_access_policy.arn
}