terraform {
  required_providers {
    # for the infra that will host Psoxy instances
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.12"
    }
  }
}


resource "aws_s3_bucket" "input" {
  bucket = "psoxy-${var.instance_id}-input"
}

resource "aws_s3_bucket" "output" {
  bucket = "psoxy-${var.instance_id}-output"
}

module "psoxy-file-handler" {
  source = "../aws-psoxy-instance"

  function_name        = "psoxy-${var.instance_id}"
  handler_class        = "co.worklytics.psoxy.S3Handler"
  source_kind          = var.source_kind
  path_to_function_zip = var.path_to_function_zip
  function_zip_hash    = var.function_zip_hash
  path_to_config       = var.path_to_config
  api_caller_role_arn  = var.api_caller_role_arn
  api_caller_role_name = var.api_caller_role_arn_name
  aws_assume_role_arn  = var.aws_assume_role_arn
  example_api_calls    = [] #None, as this function is called through the S3 event

  parameters = []

  environment_variables = {
    INPUT_BUCKET  = aws_s3_bucket.input.bucket,
    OUTPUT_BUCKET = aws_s3_bucket.output.bucket
  }
}

resource "aws_lambda_permission" "allow_input_bucket" {
  statement_id  = "AllowExecutionFromS3Bucket"
  action        = "lambda:InvokeFunction"
  function_name = module.psoxy-file-handler.function_arn
  principal     = "s3.amazonaws.com"
  source_arn    = aws_s3_bucket.input.arn
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
}

resource "aws_iam_role_policy_attachment" "caller_bucket_access_policy" {
  role       = var.api_caller_role_arn
  policy_arn = aws_iam_policy.output_bucket_read.arn
}
