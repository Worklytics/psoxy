resource "google_project_iam_custom_role" "secret_manager_updater" {
  project     = var.project_id
  role_id     = "SecretManagerAccess"
  title       = "Access for updating and reading secrets"
  description = "Access for updating and reading secrets because updating secrets are only available as roles/secretmanager.admin"

  permissions = [
    "resourcemanager.projects.get",
    "secretmanager.secrets.get",
    "secretmanager.secrets.getIamPolicy",
    "secretmanager.secrets.list",
    "secretmanager.secrets.update"
  ]
}