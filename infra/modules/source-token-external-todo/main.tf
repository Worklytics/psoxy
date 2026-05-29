
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

# NOTE: local_file resource was moved to root module. todos_as_local_files/todo_step are no-ops here.
# TODO: remove deprecated variables/outputs in 0.7

output "next_todo_step" {
  value       = var.todo_step + 1
  description = "[DEPRECATED - todo ordering now handled at root module level via todo_content stage indices. TODO: remove in 0.7]"
}

output "todo" {
  value       = local.todo_content
  description = "[DEPRECATED - use todo_content output instead. TODO: remove in 0.7]"
}

output "todo_content" {
  description = "Structured todo content to be written to local files by root module. List of stages; each stage is a list of {name, content, file_permission} objects."
  value = [[
    {
      name            = "setup ${var.source_id}"
      content         = local.todo_content
      file_permission = null
    }
  ]]
}
