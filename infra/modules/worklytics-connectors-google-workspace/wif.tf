# Workload Identity Federation for GWS API clients (no downloaded SA keys).
# AWS host: pool + AWS provider + a GCP SA that Lambdas impersonate; that SA has Token Creator
# on each DWD-enabled connector SA and calls IAM signJwt.
# GCP host: no WIF pool; Cloud Function attached SAs are granted Token Creator in the example
# (google-workspace.tf) after gcp-host creates them.

locals {
  # Pool / provider IDs are project-global; always include environment_id (or psoxy- fallback).
  wif_pool_id = substr(
    replace(lower(length(var.environment_id) > 0 ? "${var.environment_id}-gws-aws" : "psoxy-gws-aws"), "_", "-"),
    0,
    32
  )
  wif_provider_id = substr(
    replace(lower(length(var.environment_id) > 0 ? "${var.environment_id}-aws" : "psoxy-aws"), "_", "-"),
    0,
    32
  )
  wif_runtime_sa_id = trim(substr(
    replace(lower("${local.environment_id_prefix}gws-wif"), "_", "-"),
    0,
    30
  ), "-")
  wif_pool_display_name       = substr(length(var.environment_id) > 0 ? "${var.environment_id} GWS AWS WIF" : "Psoxy GWS AWS WIF", 0, 32)
  wif_provider_display_name   = substr(length(var.environment_id) > 0 ? "${var.environment_id} AWS" : "Psoxy AWS", 0, 32)
  wif_runtime_sa_display_name = substr(length(var.environment_id) > 0 ? "${var.environment_id} GWS WIF runtime" : "Psoxy GWS WIF runtime", 0, 100)

  gcp_wif_audience = local.use_aws_wif ? "//iam.googleapis.com/${google_iam_workload_identity_pool_provider.aws[0].name}" : null
  gcp_wif_service_account_impersonation_url = local.use_aws_wif ? (
    "https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/${google_service_account.wif_runtime[0].email}:generateAccessToken"
  ) : null

  wif_required_services = toset(concat(
    local.use_wif ? ["iamcredentials.googleapis.com"] : [],
    local.use_aws_wif ? ["sts.googleapis.com"] : [],
  ))
}

resource "google_project_service" "wif" {
  for_each = local.wif_required_services

  project                    = var.gcp_project_id
  service                    = each.key
  disable_dependent_services = false
  disable_on_destroy         = false
}

resource "google_iam_workload_identity_pool" "aws" {
  count = local.use_aws_wif ? 1 : 0

  project                   = var.gcp_project_id
  workload_identity_pool_id = local.wif_pool_id
  display_name              = local.wif_pool_display_name
  description               = "Federate AWS proxy Lambdas to GCP for Google Workspace signJwt"

  depends_on = [google_project_service.wif]
}

resource "google_iam_workload_identity_pool_provider" "aws" {
  count = local.use_aws_wif ? 1 : 0

  project                            = var.gcp_project_id
  workload_identity_pool_id          = google_iam_workload_identity_pool.aws[0].workload_identity_pool_id
  workload_identity_pool_provider_id = local.wif_provider_id
  display_name                       = local.wif_provider_display_name
  attribute_mapping = {
    "google.subject"        = "assertion.arn"
    "attribute.aws_role"    = "assertion.arn.contains('assumed-role/') ? assertion.arn.extract('assumed-role/{role}/') : assertion.arn"
    "attribute.aws_account" = "assertion.account"
  }
  aws {
    account_id = var.aws_account_id
  }
}

resource "google_service_account" "wif_runtime" {
  count = local.use_aws_wif ? 1 : 0

  project      = var.gcp_project_id
  account_id   = local.wif_runtime_sa_id
  display_name = local.wif_runtime_sa_display_name
  description  = "Impersonated by AWS proxy Lambdas via WIF; Token Creator on GWS DWD service accounts"
}

resource "google_service_account_iam_member" "aws_can_impersonate_wif_runtime" {
  count = local.use_aws_wif ? 1 : 0

  service_account_id = google_service_account.wif_runtime[0].name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.aws[0].name}/attribute.aws_account/${var.aws_account_id}"
}

resource "google_service_account_iam_member" "wif_runtime_can_sign_jwt" {
  for_each = {
    for k, v in module.google_workspace_connection : k => v if local.use_aws_wif
  }

  service_account_id = each.value.service_account_id
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = "serviceAccount:${google_service_account.wif_runtime[0].email}"
}