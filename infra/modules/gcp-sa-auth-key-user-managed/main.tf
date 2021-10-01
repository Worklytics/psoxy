


resource "google_service_account_iam_member" "grant_serviceAccountKeyAdmin_on_environment-sa" {
  member             = "group:${var.key_admin_group_email}"
  role               = "roles/iam.serviceAccountKeyAdmin"
  service_account_id = var.
}

