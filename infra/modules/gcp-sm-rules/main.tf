# stores Psoxy RULES as GCP Secret Manager Secret

# not the 'right' solution, as this data isn't secret. But there's no "Config Parameter" service
# solution in GCP really and this makes GCP setup most analogous to AWS.

locals {
  # size limits, in bytes
  # see: https://cloud.google.com/secret-manager/quotas
  secret_manager_size_limit = 65536

  # read rules from file
  rules_plain = file(var.file_path)

  # compress if necessary; but otherwise leave plain so human readable
  use_compressed   = length(local.rules_plain) > local.secret_manager_size_limit
  param_value      = local.use_compressed ? base64gzip(local.rules_plain) : local.rules_plain
}

resource "google_secret_manager_secret" "rules" {
  secret_id = "${var.prefix}RULES"

  replication {
    automatic = true # why not? nothing secret about it
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

output "rules_hash" {
  value = sha1(local.rules_plain)
}


