
# module to give users instructions on how to create a API token/key externally, and fill it in the
# proper place
#
# use case: sources which don't support connections provisioned via API (or Terraform)

locals {
  todo_content = <<EOT
# TODO - Create User-Managed Token for ${var.source_id}

Follow the following steps:

${var.connector_specific_external_steps}

${join("\n", var.additional_steps)}
EOT
}

resource "local_file" "source_connection_instructions" {
  count = var.todos_as_local_files ? 1 : 0

  filename = "TODO ${var.todo_step} - setup ${var.source_id}.md"
  content  = local.todo_content
}

output "next_todo_step" {
  value = var.todo_step + 1
}

output "todo" {
  value = local.todo_content
}
