

# TODO: extract this to its own repo or something, so can consume from our main infra repo. it's
# similar to src/modules/google-workspace-dwd-connector/main.tf in the main infra repo

resource "google_service_account_iam_member" "grant_tokenCreator_on_dwd-connector-sa" {
  for_each  = var.deployment_sa_emails

  member             = "serviceAccount:${each.value}"
  role               = "roles/iam.serviceAccountTokenCreator"
  service_account_id = google_service_account.connector-sa.id
}
