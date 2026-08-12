# genMetadata Bedrock cost controls (infra-only; Java degrades when InvokeModel is denied)

check "gen_metadata_backend_aws_only" {
  assert {
    condition = alltrue([
      for k, backend in local.api_connector_gen_metadata_backend :
      backend == null || backend == "bedrock"
    ])
    error_message = "aws-host genMetadata backend must be \"bedrock\" (local/Jlama abandoned; Vertex is GCP-only)."
  }
}

locals {
  gen_metadata_budget_enabled = (
    local.gen_metadata_uses_bedrock
    && var.gen_metadata_daily_cost_limit_usd != null
    && var.gen_metadata_daily_cost_limit_usd > 0
  )

  # Budget actions require at least one subscriber; auto-deny only when emails are configured.
  gen_metadata_budget_action_enabled = (
    local.gen_metadata_budget_enabled
    && length(var.gen_metadata_budget_alert_emails) > 0
    && length(local.gen_metadata_bedrock_role_names) > 0
  )

  gen_metadata_bedrock_role_names = [
    for k, backend in local.api_connector_gen_metadata_backend :
    module.api_connector[k].instance_role_name
    if backend == "bedrock"
  ]
}

resource "aws_iam_policy" "gen_metadata_bedrock_deny" {
  count = local.gen_metadata_budget_action_enabled ? 1 : 0

  name        = "${module.env_id.id}-gen-metadata-bedrock-deny"
  description = "Deny Bedrock invoke when genMetadata daily budget is exceeded"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid    = "DenyBedrockAfterBudget"
      Effect = "Deny"
      Action = [
        "bedrock:InvokeModel",
        "bedrock:Converse",
        "bedrock:InvokeModelWithResponseStream",
        "bedrock:ConverseStream",
      ]
      Resource = "*"
    }]
  })
}

resource "aws_iam_role" "gen_metadata_budgets_action" {
  count = local.gen_metadata_budget_action_enabled ? 1 : 0

  name = "${module.env_id.id}-gen-metadata-budgets"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "budgets.amazonaws.com" }
      Action    = "sts:AssumeRole"
      Condition = {
        StringEquals = {
          "aws:SourceAccount" = data.aws_caller_identity.current.account_id
        }
        ArnLike = {
          "aws:SourceArn" = "arn:aws:budgets::${data.aws_caller_identity.current.account_id}:budget/*"
        }
      }
    }]
  })
  permissions_boundary = var.iam_roles_permissions_boundary
}

resource "aws_iam_role_policy" "gen_metadata_budgets_action" {
  count = local.gen_metadata_budget_action_enabled ? 1 : 0

  name = "ApplyGenMetadataBudgetDeny"
  role = aws_iam_role.gen_metadata_budgets_action[0].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["iam:AttachRolePolicy", "iam:DetachRolePolicy"]
        Resource = [for name in local.gen_metadata_bedrock_role_names : "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/${name}"]
      },
      {
        Effect   = "Allow"
        Action   = ["iam:GetPolicy", "iam:GetPolicyVersion"]
        Resource = aws_iam_policy.gen_metadata_bedrock_deny[0].arn
      }
    ]
  })
}

resource "aws_budgets_budget" "gen_metadata_bedrock_daily" {
  count = local.gen_metadata_budget_enabled ? 1 : 0

  name         = "${module.env_id.id}-gen-metadata-bedrock-daily"
  budget_type  = "COST"
  limit_amount = tostring(var.gen_metadata_daily_cost_limit_usd)
  limit_unit   = "USD"
  time_unit    = "DAILY"

  cost_filter {
    name = "Service"
    values = [
      "Amazon Bedrock",
    ]
  }

  dynamic "notification" {
    for_each = length(var.gen_metadata_budget_alert_emails) > 0 ? [80, 100] : []
    content {
      comparison_operator        = "GREATER_THAN"
      threshold                  = notification.value
      threshold_type             = "PERCENTAGE"
      notification_type          = "ACTUAL"
      subscriber_email_addresses = var.gen_metadata_budget_alert_emails
    }
  }
}

resource "aws_budgets_budget_action" "gen_metadata_bedrock_deny" {
  count = local.gen_metadata_budget_action_enabled ? 1 : 0

  budget_name        = aws_budgets_budget.gen_metadata_bedrock_daily[0].name
  action_type        = "APPLY_IAM_POLICY"
  approval_model     = "AUTOMATIC"
  notification_type  = "ACTUAL"
  execution_role_arn = aws_iam_role.gen_metadata_budgets_action[0].arn

  action_threshold {
    action_threshold_type  = "PERCENTAGE"
    action_threshold_value = 100
  }

  definition {
    iam_action_definition {
      policy_arn = aws_iam_policy.gen_metadata_bedrock_deny[0].arn
      roles      = local.gen_metadata_bedrock_role_names
    }
  }

  dynamic "subscriber" {
    for_each = var.gen_metadata_budget_alert_emails
    content {
      address           = subscriber.value
      subscription_type = "EMAIL"
    }
  }

  depends_on = [aws_iam_role_policy.gen_metadata_budgets_action]
}
