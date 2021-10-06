
# set up authenticate the GCP SA using a user-managed key

# only thing for now is to give the user group authorized to manage the key the permission to
# do so
resource "google_service_account_iam_member" "grant_serviceAccountKeyAdmin_on_environment-sa" {
  member             = "group:${var.key_admin_group_email}"
  role               = "roles/iam.serviceAccountKeyAdmin"
  service_account_id = var.service_account_id
}



