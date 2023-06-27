
# uncomment to use encryption for S3 buckets and have it work properly for Cloud Watch Logs


#locals {
#  # TODO: can eliminate this if test tool doesn't assume role when uploading to bucket
#  testing_policy_statements = var.provision_testing_infra ? [
#    {
#      "Sid": "Allow Test Users to Use Key",
#      "Effect": "Allow",
#      "Principal": { # tests
#        "AWS": "arn:aws:iam::${var.aws_account_id}:role/${module.psoxy.caller_role_name}"
#      },
#      "Action": "kms:*",
#      "Resource": "*"
#    }
#  ] : []
#
#  # S3 bucket policy statements for bulk writer instances
#  # explicitly allow each instance's exec role to use the key to encrypt, as it needs to write to
#  # the output buckets
#  bulk_writer_policy_statements = [
#    for instance in module.psoxy.bulk_connector_instances : {
#      "Effect" : "Allow",
#      "Principal" : {
#         "AWS" : instance.instance_role_arn
#      },
#      "Action" : [
#        "kms:Encrypt",
#        "kms:GenerateDataKey",
#      ],
#      "Resource" : "*"
#    }
#  ]
#}
#
#resource "aws_kms_key_policy" "psoxy" {
#  key_id = var.project_aws_kms_key_arn
#  policy = jsonencode(
#    {
#      "Version" : "2012-10-17",
#      "Id" : "psoxy-key-policy",
#      "Statement" : concat(
#        [
#          # to allow Terraform to manage the key
#          {
#            "Sid": "Allow IAM Users to Manage Key",
#            "Effect": "Allow",
#            "Principal": {
#              "AWS": "arn:aws:iam::${var.aws_account_id}:root"
#            },
#            "Action": "kms:*",
#            "Resource": "*"
#          },
#          # to use for Cloud Watch Logs
#          {
#            "Effect" : "Allow",
#            "Principal" : {
#              "Service" : "logs.${var.aws_region}.amazonaws.com"
#            },
#            "Action" : [
#              "kms:Encrypt",
#              "kms:Decrypt",
#              "kms:ReEncrypt",
#              "kms:GenerateDataKey",
#              "kms:Describe"
#            ],
#            "Resource" : "*"
#          }
#      ],
#      local.bulk_writer_policy_statements,
#      local.testing_policy_statements
#      )
#    })
#}
#
#resource "aws_s3_bucket_server_side_encryption_configuration" "input_bucket_encryption" {
#  for_each = module.psoxy.bulk_connector_instances
#
#  bucket = each.value.input_bucket
#
#  rule {
#    apply_server_side_encryption_by_default {
#      sse_algorithm = "aws:kms"
#      kms_master_key_id = var.project_aws_kms_key_arn
#    }
#  }
#}
#
#resource "aws_s3_bucket_server_side_encryption_configuration" "sanitized_bucket_encryption" {
#  for_each = module.psoxy.bulk_connector_instances
#
#  bucket = each.value.sanitized_bucket
#
#  rule {
#    apply_server_side_encryption_by_default {
#      sse_algorithm = "aws:kms"
#      kms_master_key_id = var.project_aws_kms_key_arn
#    }
#  }
#}
