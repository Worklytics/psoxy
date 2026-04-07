# stores rules as GCP Secret Manager Secret
#
# DEPRECATED in v0.6.0 - Use gcp-pm-rules module instead.
# This module is retained ONLY for backward compatibility with existing deployments.
# New deployments should use GCP Parameter Manager (gcp-pm-rules) for non-secret config.

locals {
  # size limits, in bytes
  # see: https://cloud.google.com/secret-manager/quotas
  secret_manager_size_limit = 65536

  # read rules from file or use content directly
  rules_plain = var.content != null ? var.content : file(var.file_path)

  # compress if necessary; but otherwise leave plain so human readable
  use_compressed = length(local.rules_plain) > local.secret_manager_size_limit
  param_value    = local.use_compressed ? base64gzip(local.rules_plain) : local.rules_plain
}

resource "google_secret_manager_secret" "rules" {
  project   = var.project_id
  secret_id = "${var.prefix}RULES"

  # no need to encrypt with CMEK; it's actually configuration value, not "secret"

  replication {
    auto {

    }
  }
}

resource "google_secret_manager_secret_version" "rules" {
  secret      = google_secret_manager_secret.rules.id
  secret_data = local.param_value

  lifecycle {
    precondition {
      condition     = length(local.param_value) < local.secret_manager_size_limit
      error_message = "Rules are too big to store as a Secret Manager secret."
    }
  }
}



output "rules_hash" {
  value = sha1(local.rules_plain)
}


