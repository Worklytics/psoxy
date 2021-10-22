
# set up authenticate the GCP SA using a user-managed key

# only thing for now is to give the user group authorized to manage the key the permission to
# do so
resource "google_service_account_iam_member" "grant_serviceAccountKeyAdmin_on_targetSA" {
  member             = "group:${var.key_admin_group_email}"
  role               = "roles/iam.serviceAccountKeyAdmin"
  service_account_id = var.service_account_id
}

resource "google_secret_manager_secret" "service-account-key" {
  project   = var.secret_project
  secret_id = var.secret_id

  replication {
    automatic = true
  }
}

resource "google_secret_manager_secret_iam_member" "grant_secretVersionAdder_on_saKeySecret" {
  project   = var.secret_project
  secret_id = var.secret_id
  member    = "group:${var.key_admin_group_email}"
  role      = "roles/secretmanager.secretVersionAdder"
}

resource "local_file" "todo" {
  filename = "TODO - create key for ${var.service_account_id}.md"
  content  = <<EOT
Create a key for ${var.service_account_id} and upload it to Secret Manager. You can do this from the
GCP console, or using the `gcloud` tool as described below:

## via `gcloud` command line

Run the following commands in a terminal. You can use your local machine; or to maximize security,
you could use a GCP Compute Engine instance or [Cloud Shell](https://cloud.google.com/shell) - which would avoid the key ever
transiting your local machine.

```shell
gcloud iam service-accounts keys create --project=${var.project_id} --iam-account=${var.service_account_id} key.json

gcloud secrets versions add ${var.secret_id} --project=${var.secret_project} --data-file=key.json
```

Last, don't forget to destroy your local copy!
```shell
rm key.json
```
EOT
}
