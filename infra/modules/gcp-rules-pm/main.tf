# stores rules as GCP Parameter Manager Parameter
# Parameter Manager is the correct service for non-secret configuration values like RULES.

resource "google_parameter_manager_parameter" "rules" {
  project      = var.project_id
  parameter_id = "${var.prefix}RULES"
  format       = "UNFORMATTED"
}

resource "google_parameter_manager_parameter_version" "rules" {
  parameter            = google_parameter_manager_parameter.rules.id
  parameter_version_id = "latest"
  parameter_data       = var.content

  lifecycle {
    create_before_destroy = true
  }
}

output "rules_hash" {
  value = sha1(var.content)
}

output "parameter_id" {
  value = google_parameter_manager_parameter.rules.parameter_id
}
