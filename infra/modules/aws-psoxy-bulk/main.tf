terraform {
  required_providers {
    # for the infra that will host Psoxy instances
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.12"
    }
  }
}

module "psoxy_lambda" {
  source = "../aws-psoxy-lambda"

  function_name        = "psoxy-${var.instance_id}"
  handler_class        = "co.worklytics.psoxy.S3Handler"
  timeout_seconds      = 600 # 10 minutes
  memory_size_mb       = 512
  path_to_config       = var.path_to_config
  path_to_function_zip = var.path_to_function_zip
  function_zip_hash    = var.function_zip_hash
  aws_assume_role_arn  = var.aws_assume_role_arn
  source_kind          = var.source_kind
  parameters           = []
  environment_variables = {
    INPUT_BUCKET  = aws_s3_bucket.input.bucket,
    OUTPUT_BUCKET = aws_s3_bucket.output.bucket
  }
}



resource "aws_s3_bucket" "input" {
  bucket = "psoxy-${var.instance_id}-input"
}

/*
 * USE CASE: By default config as is works. But customer attached own rules to bucket, that seems to
 * supersede default, so this piece needs to be added to the bucket policies too.
 *
 * TODO: add optional variable of extra statements to add to bucket policy, array_merge policy
 *       statements to support customer adding more stuff
 */
locals {
  read_policy_json = jsonencode({
    "Version" : "2012-10-17",
    "Statement" : [
      {
        "Sid" : "GrantLambdaAccessToInputBucket",
        "Action" : [
          "s3:GetObject"
        ],
        "Effect" : "Allow",
        "Resource" : [
          "${aws_s3_bucket.input.arn}/*"
        ],
        "Principal" : {
          "AWS" : [
            "${module.psoxy_lambda.iam_role_for_lambda_arn}"
          ]
        }
      }
    ]
  })
}

resource "aws_s3_bucket_policy" "read_policy_to_execution_role" {
  bucket = aws_s3_bucket.input
  policy = local.read_policy_json
}


resource "aws_iam_policy" "read_policy_to_execution_role" {
  name        = "BucketRead_${aws_s3_bucket.input.id}"
  description = "Allow principal to read from input bucket: ${aws_s3_bucket.input.id}"

  policy = jsonencode(
    {
      "Version" : "2012-10-17",
      "Statement" : [
        {
          "Sid" : "GrantLambdaAccessToInputBucket",
          "Action" : [
            "s3:GetObject"
          ],
          "Effect" : "Allow",
          "Resource" : [
            "${aws_s3_bucket.input.arn}/*"
          ],
          "Principal" : {
            "AWS" : [
              "${module.psoxy_lambda.iam_role_for_lambda_arn}"
            ]
          }
        }
      ]
  })
}


resource "aws_s3_bucket" "output" {
  bucket = "psoxy-${var.instance_id}-output"
}


# cloudwatch group per lambda function
resource "aws_cloudwatch_log_group" "lambda-log" {
  name              = "/aws/lambda/${module.psoxy_lambda.function_name}"
  retention_in_days = 7
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
}

resource "aws_iam_role_policy_attachment" "read_policy_for_import_bucket" {
  role       = module.psoxy_lambda.iam_role_for_lambda_name
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
}



resource "aws_iam_role_policy_attachment" "write_policy_for_output_bucket" {
  role       = module.psoxy_lambda.iam_role_for_lambda_name
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
}

resource "aws_iam_role_policy_attachment" "caller_bucket_access_policy" {
  role       = var.api_caller_role_arn
  policy_arn = aws_iam_policy.output_bucket_read.arn
}
