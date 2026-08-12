# genMetadata Vertex cost controls (infra alerts only; Java degrades on 403/429)

check "gen_metadata_backend_gcp_only" {
  assert {
    condition = alltrue([
      for k, backend in local.api_connector_gen_metadata_backend :
      backend == null || backend == "vertex"
    ])
    error_message = "gcp-host genMetadata backend must be \"vertex\" (local/Jlama abandoned; Bedrock is AWS-only)."
  }
}

data "google_project" "gen_metadata" {
  count      = local.gen_metadata_uses_vertex ? 1 : 0
  project_id = var.gcp_project_id
}

locals {
  gen_metadata_vertex_sa_emails = {
    for k, backend in local.api_connector_gen_metadata_backend : k => google_service_account.api_connectors[k].email
    if backend == "vertex"
  }

  gen_metadata_budget_enabled = (
    local.gen_metadata_uses_vertex
    && var.gen_metadata_daily_cost_limit_usd != null
    && var.gen_metadata_daily_cost_limit_usd > 0
    && var.billing_account_id != null
    && length(trimspace(var.billing_account_id)) > 0
  )

  # GCP billing budgets are monthly; approximate daily × 30.
  gen_metadata_monthly_budget_usd = local.gen_metadata_budget_enabled ? ceil(var.gen_metadata_daily_cost_limit_usd * 30) : null
}

resource "google_project_service" "aiplatform" {
  count = local.gen_metadata_uses_vertex ? 1 : 0

  project                    = var.gcp_project_id
  service                    = "aiplatform.googleapis.com"
  disable_dependent_services = false
  disable_on_destroy         = false
}

resource "google_project_service" "billingbudgets" {
  count = local.gen_metadata_budget_enabled ? 1 : 0

  project                    = var.gcp_project_id
  service                    = "billingbudgets.googleapis.com"
  disable_dependent_services = false
  disable_on_destroy         = false
}

resource "google_project_iam_member" "gen_metadata_vertex_user" {
  for_each = local.gen_metadata_vertex_sa_emails

  project = var.gcp_project_id
  role    = "roles/aiplatform.user"
  member  = "serviceAccount:${each.value}"

  depends_on = [google_project_service.aiplatform]
}

resource "google_monitoring_notification_channel" "gen_metadata_budget_email" {
  for_each = local.gen_metadata_budget_enabled ? toset(var.gen_metadata_budget_alert_emails) : toset([])

  project      = var.gcp_project_id
  display_name = "genMetadata Vertex budget: ${each.value}"
  type         = "email"
  labels = {
    email_address = each.value
  }
}

resource "google_billing_budget" "gen_metadata_vertex_monthly" {
  count = local.gen_metadata_budget_enabled ? 1 : 0

  billing_account = var.billing_account_id
  display_name    = "${var.environment_name}-gen-metadata-vertex-monthly"

  budget_filter {
    projects = ["projects/${data.google_project.gen_metadata[0].number}"]
    # Vertex AI service id
    services = ["services/6F81-5844-456A"]
  }

  amount {
    specified_amount {
      currency_code = "USD"
      units         = tostring(local.gen_metadata_monthly_budget_usd)
    }
  }

  threshold_rules {
    threshold_percent = 0.5
  }
  threshold_rules {
    threshold_percent = 0.8
  }
  threshold_rules {
    threshold_percent = 1.0
  }

  dynamic "all_updates_rule" {
    for_each = length(var.gen_metadata_budget_alert_emails) > 0 ? [1] : []
    content {
      monitoring_notification_channels = [
        for ch in google_monitoring_notification_channel.gen_metadata_budget_email : ch.id
      ]
      disable_default_iam_recipients = true
    }
  }

  depends_on = [google_project_service.billingbudgets]
}

output "gen_metadata_vertex_budget_todo" {
  description = "TODO when Vertex genMetadata is enabled but billing_account_id is unset (budget skipped)."
  value = (
    local.gen_metadata_uses_vertex
    && var.gen_metadata_daily_cost_limit_usd != null
    && var.gen_metadata_daily_cost_limit_usd > 0
    && (var.billing_account_id == null || length(trimspace(var.billing_account_id)) == 0)
    ) ? trimspace(<<-EOT
	## Configure Vertex genMetadata billing budget

	Vertex genMetadata is enabled, but `billing_account_id` was not set on gcp-host, so no Cloud Billing budget was created.

	Set `billing_account_id` (and optionally `gen_metadata_budget_alert_emails`) and re-apply to provision a monthly Vertex AI budget sized from `gen_metadata_daily_cost_limit_usd` × 30.

	GCP has no daily billing budget; alerts are monthly only. There is no infra IAM auto-deny for Vertex (unlike AWS Bedrock budget actions).
	EOT
  ) : null
}
