resource "google_project_iam_custom_role" "secret_manager_updater" {
  project     = var.project_id
  role_id     = "SecretManagerAccess"
  title       = "Access for updating and reading secrets"
  description = "Role to grant on secret that is to be managed by a Psoxy instance (cloud function); subset of roles/secretmanager.admin, to support reading/updating the secret"

  permissions = [
    "resourcemanager.projects.get",
    "secretmanager.secrets.get",
    "secretmanager.secrets.getIamPolicy",
    "secretmanager.secrets.list",
    "secretmanager.secrets.update"
  ]
}