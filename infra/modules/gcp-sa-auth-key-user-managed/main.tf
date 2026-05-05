
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
    user_managed {
      dynamic "replicas" {
        for_each = var.replica_regions
        content {
          location = replicas.value
        }
      }
    }
  }

  lifecycle {
    ignore_changes = [
      replication, # for backwards compatibility; replication can't be changed after secrets created
      labels
    ]
  }
}

resource "google_secret_manager_secret_iam_member" "grant_secretVersionAdder_on_saKeySecret" {
  project   = var.secret_project
  secret_id = var.secret_id
  member    = "group:${var.key_admin_group_email}"
  role      = "roles/secretmanager.secretVersionAdder"
}

# NOTE: local_file resource was moved to root module.
# TODO: remove deprecated variables/outputs in 0.7

locals {
  todo_content = <<EOT
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

output "todo_content" {
  description = "Structured todo content to be written to local files by root module. List of stages; each stage is a list of {name, content, file_permission} objects."
  value = [[
    {
      name            = "create key for ${var.service_account_id}"
      content         = local.todo_content
      file_permission = null
    }
  ]]
}
