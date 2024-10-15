# stores rules as GCP Secret Manager Secret

# not the 'right' solution, as this data isn't secret. But there's no "Config Parameter" service
# solution in GCP really and this makes GCP setup most analogous to AWS.

locals {
  # size limits, in bytes
  # see: https://cloud.google.com/secret-manager/quotas
  secret_manager_size_limit = 65536

  # read rules from file
  rules_plain = file(var.file_path)

  # compress if necessary; but otherwise leave plain so human readable
  use_compressed = length(local.rules_plain) > local.secret_manager_size_limit
  param_value    = local.use_compressed ? base64gzip(local.rules_plain) : local.rules_plain
}

resource "google_secret_manager_secret" "rules" {
  project   = var.project_id
  secret_id = "${var.prefix}RULES"
  labels    = var.default_labels

  # no need to encrypt with CMEK; it's actually configuration value, not "secret"

  replication {
    auto {

    }
  }

  lifecycle {
    ignore_changes = [
      labels
    ]
  }
}

resource "google_secret_manager_secret_version" "rules" {
  secret      = google_secret_manager_secret.rules.id
  secret_data = local.param_value

  lifecycle {
    precondition {
      condition     = length(local.param_value) < local.secret_manager_size_limit
      error_message = "Rules on file ${var.file_path} are too big to store"
    }
  }
}

# so can list versions, get latest
resource "google_secret_manager_secret_iam_member" "view" {
  member    = "serviceAccount:${var.instance_sa_email}"
  role      = "roles/secretmanager.viewer"
  secret_id = google_secret_manager_secret.rules.id
}

# access versions
resource "google_secret_manager_secret_iam_member" "access" {
  member    = "serviceAccount:${var.instance_sa_email}"
  role      = "roles/secretmanager.secretAccessor"
  secret_id = google_secret_manager_secret.rules.id
}

output "rules_hash" {
  value = sha1(local.rules_plain)
}


