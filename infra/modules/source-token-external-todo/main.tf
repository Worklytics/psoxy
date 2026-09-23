
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

locals {
  todo_filename = "TODO ${var.todo_step} - setup ${var.source_id}.md"
}

# DEPRECATED: this local_file TODO is deprecated and will be removed in 0.8.
# Write the same file with ./generate-todos.sh, which reads it from terraform output.
resource "local_file" "source_connection_instructions" {
  count = var.todos_as_local_files ? 1 : 0

  filename = local.todo_filename
  content  = local.todo_content
}

output "next_todo_step" {
  value = var.todo_step + 1
}

output "todo" {
  value = local.todo_content
}

output "todo_files" {
  description = "TODO markdown files (filename => content) for ./generate-todos.sh. The local_file copy is deprecated and will be removed in 0.8."
  value = {
    (local.todo_filename) = local.todo_content
  }
}
