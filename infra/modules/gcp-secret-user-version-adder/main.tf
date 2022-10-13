locals {
  grant_expires = timeadd(timestamp(), var.grant_duration)
}

# short-lived grant to human users to populate the token, as only needs to be set once
resource "google_secret_manager_secret_iam_member" "grant_secretVersionAdd_on_accessToken_to_users" {
  for_each = toset(var.user_emails)

  role      = "roles/secretmanager.secretVersionAdder"
  project   = var.project_id
  secret_id = var.secret_id
  member    = "user:${each.value}"

  condition {
    title       = "until ${local.grant_expires}"
    expression  = "request.time < timestamp(\"${local.grant_expires}\")"
    description = "short-lived grant to human users"
  }
}
