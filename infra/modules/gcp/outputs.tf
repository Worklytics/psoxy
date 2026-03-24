output "builder_sa_id" {
  value       = "projects/${var.project_id}/serviceAccounts/${local.builder_sa_email}"
  description = "The fully-qualified ID of the builder service account used for Cloud Functions."
}
