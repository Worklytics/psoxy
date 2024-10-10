
# uncomment to use encryption for S3 buckets and have it work properly for Cloud Watch Logs

# this file sets up necessary stuff for S3 custom encryption (as opposed to AWS default) with a
# architecture style that is more 'proper' Terraform - eg, composition of resources.

#resource "aws_kms_key" "example_key" {
#  description = "key for encrypting S3 buckets"
#}
#
#
#
#locals {
#  key_arn = aws_kms_key.example_key.arn # alternatively, use ar.project_aws_kms_key_arn
#
#  # TODO: can eliminate this if test tool doesn't assume role when uploading to bucket
#  testing_policy_statements = var.provision_testing_infra ? [
#    {
#      "Sid": "Allow Test Users to Use Key",
#      "Effect": "Allow",
#      "Principal": { # tests
#        "AWS": "arn:aws:iam::${var.aws_account_id}:role/${module.psoxy.caller_role_name}"
#      },
#      "Action": "kms:*",
#      "Resource": local.key_arn
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
#      "Resource" : local.key_arn
#    }
#  ]
#  # for bulk case, proxy caller role must be able to READ from the sanitized buckets, requiring
#  # decrypt permission for the key
#  proxy_caller_policy_statements = [
#    for instance in module.psoxy.bulk_connector_instances : {
#      "Effect" : "Allow",
#      "Principal" : {
#        "AWS" : module.psoxy.caller_role_arn
#      },
#      "Action" : [
#        "kms:Decrypt",
#      ],
#      "Resource" : aws_kms_key.example_key.arn
#    }
#  ]
#}
#
#resource "aws_kms_key_policy" "proxy" {
#  key_id = local.key_arn
#  policy = jsonencode(
#    {
#      "Version" : "2012-10-17",
#      "Id" : "proxy-key-policy",
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
#            "Resource": local.key_arn
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
#            "Resource" : local.key_arn
#          }
#      ],
#      local.bulk_writer_policy_statements,
#      local.testing_policy_statements,
#      local.proxy_caller_policy_statements
#      )
#    })
#}
#
## concisely set S3 encryption for all buckets
#resource "aws_s3_bucket_server_side_encryption_configuration" "bulk_buckets" {
#  for_each = merge(
#    { for k, v in module.psoxy.bulk_connector_instances: "${k}_input" => v.input_bucket } ,
#    { for k, v in module.psoxy.bulk_connector_instances: "${k}_sanitized" => v.sanitized_bucket } ,
#    module.psoxy.lookup_output_buckets,
#  )
#
#  bucket = each.value
#
#  rule {
#    apply_server_side_encryption_by_default {
#      kms_master_key_id = aws_kms_key.example_key.id
#      sse_algorithm     = "aws:kms"
#    }
#  }
#}
