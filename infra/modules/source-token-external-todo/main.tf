
# module to give users instructions on how to create a API token/key externally, and fill it in the
# proper place
#
# use case: sources which don't support connections provisioned via API (or Terraform)


resource "local_file" "source_connection_instructions" {
  filename = "TODO ${var.todo_step} - ${var.source_id}.md"
  content  = <<EOT
# TODO - Create User-Managed Token for ${var.source_id}

Follow the following steps:

${var.connector_specific_external_steps}

Then:
   1. ensure that you have sufficient permissions to set the value of the `${var.token_secret_id}`
      in ${var.host_cloud == "aws" ? "AWS Systems Manager Parameter" : "GCP Secret Manager Secret"};
      if not, send these instructions (and the value you created previously) to someone who does via
      a secure means
   2. use ${upper(var.host_cloud)} console or CLI, fill the token value you created above as the
      value of `${var.token_secret_id}`

AWS example:
```shell
aws ssm put-parameter \
--name "${var.token_secret_id}" \
--type "SecureString " \
--value "YOUR_VALUE_HERE" \
--overwrite
```

GCP example:
(passing `-` as `--data-file` will let you paste you token value to `stdin`)
```shell
gcloud secrets versions add ${var.token_secret_id} --project=YOUR_PROJECT_ID --data-file=-
```

EOT
}

output "next_todo_step" {
  value = var.todo_step + 1
}
