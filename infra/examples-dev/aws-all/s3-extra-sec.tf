
locals {
  buckets_to_secure = merge(
    { for k, v in module.psoxy.bulk_connector_instances: "${k}_input" => v.input_bucket } ,
    { for k, v in module.psoxy.bulk_connector_instances: "${k}_sanitized" => v.sanitized_bucket } ,
    module.psoxy.lookup_output_buckets,
  )
}


## concisely set S3 logging for all buckets
# enabling bucket logging is a recommended best practice for security and compliance; many scanners
# will flag its absence as a security risk.
# TODO: to enable, uncomment block below and replace 'my-log-bucket' with ID of bucket you want to log to

#resource "aws_s3_bucket_logging" "all_buckets" {
#  for_each = local.buckets_to_secure
#
#  bucket        = each.value
#  target_bucket = "my-log-bucket"
#  target_prefix = "${var.environment_name}/${each.key}/"
#}

## concisely set S3 versioning for all buckets
# enabling bucket versioning is a recommended best practice for security and compliance; many
# scanners will flag its absence as a security risk; although for proxy use-case, there's little
# need - neither -input or -sanitized buckets are the primary store of the data in question, nor
# are they intended for any kind of a backup purpose.
# to enable, uncomment block below

#resource "aws_s3_bucket_versioning" "all_buckets" {
#  for_each = local.buckets_to_secure
#
#  bucket        = each.value
#
#  versioning_configuration {
#    status = "Enabled"
#  }
#}

## concisely set secure transport bucket policy for all buckets
# - not done by default to avoid complexity, but uncomment lines below to enable
# resource "aws_s3_bucket_policy" "deny_s3_nonsecure_transport" {
#   for_each = local.buckets_to_secure
#
#   bucket = each.value
#   policy = jsonencode({
#     Version   = "2012-10-17"
#     Statement = [
#       {
#         Sid      = "DenyNonSecureTransport"
#         Effect   = "Deny"
#         Action   = ["s3:*"]
#         Principal = "*"
#         Resource =  [
#           "arn:aws:s3:::${each.value}",
#           "arn:aws:s3:::${each.value}/*"
#         ]
#         Condition = {
#           Bool = {
#             "aws:SecureTransport" = false
#           }
#         }
#       }
#     ]
#   })
# }
