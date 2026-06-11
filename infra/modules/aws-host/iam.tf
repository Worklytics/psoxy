# IAM policies for Psoxy are consolidated at the host level (one policy per principal/role),
# rather than per connector instance, to reduce policy/attachment churn.

locals {
  caller_readable_s3_bucket_ids = distinct(compact(concat(
    [for k, v in module.bulk_connector : v.sanitized_bucket],
    [for k, v in module.webhook_collectors : v.output_sanitized_bucket_id],
    [for k, v in module.api_connector : v.async_output_bucket_id if try(v.async_output_bucket_id, null) != null],
    [for k, v in module.api_connector : v.side_output_sanitized_bucket_id if try(v.side_output_sanitized_bucket_id, null) != null],
    [for k, v in module.lookup_output : v.output_bucket],
  )))

  caller_output_bucket_read_resources = flatten([
    for bucket_id in local.caller_readable_s3_bucket_ids : [
      "arn:aws:s3:::${bucket_id}",
      "arn:aws:s3:::${bucket_id}/*",
    ]
  ])

  # Lookup-table accessor roles (other than the Psoxy caller) need read access only to their
  # lookup bucket(s), not to all output buckets.
  lookup_tables_with_non_caller_accessor_roles = {
    for lookup_id, config in var.lookup_table_builders :
    lookup_id => [
      for role_name in config.sanitized_accessor_role_names :
      role_name if role_name != module.psoxy.api_caller_role_name
    ]
    if length([
      for role_name in config.sanitized_accessor_role_names :
      role_name if role_name != module.psoxy.api_caller_role_name
    ]) > 0
  }

  lookup_bucket_read_attachments = merge([
    for lookup_id, role_names in local.lookup_tables_with_non_caller_accessor_roles : {
      for role_name in toset(role_names) :
      "${lookup_id}-${role_name}" => {
        lookup_id = lookup_id
        role_name = role_name
      }
    }
  ]...)

  provision_psoxy_caller_access_policy = local.caller_requires_direct_lambda_access || length(local.caller_readable_s3_bucket_ids) > 0
}

data "aws_iam_policy_document" "lookup_bucket_read" {
  for_each = local.lookup_tables_with_non_caller_accessor_roles

  statement {
    sid    = "ReadLookupBucket"
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:ListBucket",
    ]
    resources = [
      "arn:aws:s3:::${module.lookup_output[each.key].output_bucket}",
      "arn:aws:s3:::${module.lookup_output[each.key].output_bucket}/*",
    ]
  }
}

data "aws_iam_policy_document" "psoxy_caller_access" {
  count = local.provision_psoxy_caller_access_policy ? 1 : 0

  dynamic "statement" {
    for_each = local.caller_requires_direct_lambda_access ? [1] : []
    content {
      sid    = "InvokeApiConnectors"
      effect = "Allow"
      actions = [
        "lambda:InvokeFunctionUrl",
        "lambda:InvokeFunction",
      ]
      resources = [for k, v in module.api_connector : v.function_arn]
    }
  }

  dynamic "statement" {
    for_each = length(local.caller_readable_s3_bucket_ids) > 0 ? [1] : []
    content {
      sid    = "ReadOutputBuckets"
      effect = "Allow"
      actions = [
        "s3:GetObject",
        "s3:ListBucket",
      ]
      resources = local.caller_output_bucket_read_resources
    }
  }
}

resource "aws_iam_policy" "psoxy_caller_access" {
  count = local.provision_psoxy_caller_access_policy ? 1 : 0

  name        = "${module.env_id.id}CallerAccess"
  description = "Allow Psoxy caller to invoke API connectors and read sanitized output buckets"

  policy = data.aws_iam_policy_document.psoxy_caller_access[0].json

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

resource "aws_iam_role_policy_attachment" "psoxy_caller_access" {
  count = local.provision_psoxy_caller_access_policy ? 1 : 0

  role       = module.psoxy.api_caller_role_name
  policy_arn = aws_iam_policy.psoxy_caller_access[0].arn
}

resource "aws_iam_policy" "lookup_bucket_read" {
  for_each = local.lookup_tables_with_non_caller_accessor_roles

  name        = "${module.env_id.id}LookupBucketRead_${replace(each.key, "-", "_")}"
  description = "Allow read access to lookup table bucket: ${module.lookup_output[each.key].output_bucket}"

  policy = data.aws_iam_policy_document.lookup_bucket_read[each.key].json

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

resource "aws_iam_role_policy_attachment" "lookup_bucket_read" {
  for_each = local.lookup_bucket_read_attachments

  role       = each.value.role_name
  policy_arn = aws_iam_policy.lookup_bucket_read[each.value.lookup_id].arn
}
