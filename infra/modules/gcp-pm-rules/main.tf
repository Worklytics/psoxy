# stores rules as GCP Parameter Manager Parameter
# Parameter Manager is the correct service for non-secret configuration values like RULES.

locals {
  # read rules from file or use content directly
  rules_plain = var.content != null ? var.content : file(var.file_path)
}

resource "google_parameter_manager_parameter" "rules" {
  project      = var.project_id
  parameter_id = "${var.prefix}RULES"
  format       = "UNFORMATTED"
}

resource "google_parameter_manager_parameter_version" "rules" {
  parameter            = google_parameter_manager_parameter.rules.id
  parameter_version_id = "latest"
  parameter_data       = local.rules_plain

  lifecycle {
    create_before_destroy = true
  }
}

# NOTE: IAM access to parameters is granted at the project level via the custom
# parameter_reader role in the gcp module. Per-parameter IAM is not available in
# the google provider.

output "rules_hash" {
  value = sha1(local.rules_plain)
}

output "parameter_id" {
  value = google_parameter_manager_parameter.rules.parameter_id
}
