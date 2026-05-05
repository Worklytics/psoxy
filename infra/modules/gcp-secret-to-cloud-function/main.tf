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

# NOTE: local_file resource was moved to root module.
# TODO: remove deprecated variables/outputs in 0.7

locals {
  todo_content = <<EOT
Run the following command from functions deployment directory (containing bundled JAR or pom.xml)
to finish exposing  `${local.slugified_secret_name}` to `${var.function_name}`:

```shell
gcloud beta functions deploy ${var.function_name} \\
    --project=${var.project_id} \\
    --runtime=${var.runtime} \\
    --update-secrets 'SERVICE_ACCOUNT_KEY=${var.secret_name}:${var.secret_version_number}'
```
EOT
}

output "todo_content" {
  description = "Structured todo content to be written to local files by root module. List of stages; each stage is a list of {name, content, file_permission} objects."
  value = [[
    {
      name            = "link ${local.slugified_secret_name} to ${var.function_name}"
      content         = local.todo_content
      file_permission = null
    }
  ]]
}
