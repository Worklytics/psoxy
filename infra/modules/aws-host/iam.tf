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

  lookup_accessor_role_names = distinct(flatten([
    for k, v in var.lookup_table_builders : v.sanitized_accessor_role_names
  ]))

  non_caller_lookup_accessor_role_names = [
    for role_name in local.lookup_accessor_role_names : role_name
    if role_name != module.psoxy.api_caller_role_name
  ]

  provision_psoxy_caller_access_policy = local.caller_requires_direct_lambda_access || length(local.caller_readable_s3_bucket_ids) > 0
}

data "aws_iam_policy_document" "output_bucket_read" {
  count = length(local.caller_readable_s3_bucket_ids) > 0 ? 1 : 0

  statement {
    sid    = "ReadOutputBuckets"
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:ListBucket",
    ]
    resources = local.caller_output_bucket_read_resources
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

resource "aws_iam_policy" "output_bucket_read" {
  count = length(local.caller_readable_s3_bucket_ids) > 0 ? 1 : 0

  name        = "${module.env_id.id}OutputBucketRead"
  description = "Allow read access to Psoxy output buckets"

  policy = data.aws_iam_policy_document.output_bucket_read[0].json

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

resource "aws_iam_role_policy_attachment" "output_bucket_read" {
  for_each = length(local.caller_readable_s3_bucket_ids) > 0 ? toset(local.non_caller_lookup_accessor_role_names) : toset([])

  role       = each.key
  policy_arn = aws_iam_policy.output_bucket_read[0].arn
}
