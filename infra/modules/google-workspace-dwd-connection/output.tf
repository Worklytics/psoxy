output "instance_id" {
  value = var.instance_id
}

output "service_account_id" {
  value = var.provision_service_account ? google_service_account.connector_sa[0].id : "projects/${var.project_id}/serviceAccounts/${local.expected_sa_email}"
}

output "service_account_email" {
  value = var.provision_service_account ? google_service_account.connector_sa[0].email : local.expected_sa_email
}

output "service_account_numeric_id" {
  value       = var.provision_service_account ? google_service_account.connector_sa[0].unique_id : null
  description = "OAuth client ID for domain-wide delegation; null if the service account is not provisioned by Terraform"
}

output "next_todo_step" {
  value = var.todo_step + 1
}

output "todo" {
  value = local.todo_content
}
