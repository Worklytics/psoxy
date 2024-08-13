

## concisely set S3 logging for all buckets
# TODO: to enable, uncomment block below and replace 'my-log-bucket' with ID of bucket you want to log to

#resource "aws_s3_bucket_logging" "bulk_buckets" {
#  for_each = merge(
#    { for k, v in module.psoxy.bulk_connector_instances: "${k}_input" => v.input_bucket } ,
#    { for k, v in module.psoxy.bulk_connector_instances: "${k}_sanitized" => v.sanitized_bucket } ,
#    module.psoxy.lookup_output_buckets,
#  )
#
#  bucket        = each.value
#  target_bucket = "my-log-bucket"
#  target_prefix = "${var.environment_name}/${each.key}/"
#}
