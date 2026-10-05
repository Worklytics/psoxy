# expose a Secret Manager secret to a Cloud function
#  NOTE: this effectively 're-deploys' the function just to add/update the secret, so in practice
# a batch approach is preferable. see (`modules/gcp-psoxy-cloud-function')
locals {
  slugified_secret_name = replace(var.secret_name, "/", "-")
}

resource "google_secret_manager_secret_iam_member" "grant_sa_accessor_on_secret" {
  member    = "serviceAccount:${var.service_account_email}"
  role      = "roles/secretmanager.secretAccessor"
  secret_id = var.secret_name
}

locals {
  todo_filename = "TODO ${var.function_name} - link ${local.slugified_secret_name}.md"
  todo_content  = <<EOT
Run the following command from functions deployment directory (containing bundled JAR or pom.xml)
to finish exposing  `${local.slugified_secret_name}` to `${var.function_name}`:

```shell
gcloud beta functions deploy ${var.function_name} \
    --project=${var.project_id} \
    --runtime=${var.runtime} \
    --update-secrets 'SERVICE_ACCOUNT_KEY=${var.secret_name}:${var.secret_version_number}'
```
EOT
}

# DEPRECATED: this local_file TODO is deprecated and will be removed in 0.8.
# Write the same file with ./generate-todos.sh, which reads it from terraform output.
resource "local_file" "todo" {
  filename = local.todo_filename
  content  = local.todo_content
}

output "todo_files" {
  description = "TODO markdown files (filename => content) for ./generate-todos.sh. The local_file copy is deprecated and will be removed in 0.8."
  value = {
    (local.todo_filename) = local.todo_content
  }
}
