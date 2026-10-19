# TODO markdown and test scripts are written here, not inside the modules.
# Every module call in this example passes todos_as_local_files = false, so those
# modules do not also create local_file resources for the same paths. A second writer,
# or a filename taken from another local_file, deletes the file on apply.
#
# gcp-host, aws-host, and the connector modules still create the files when
# todos_as_local_files is true. Existing deployments that pass the variable through
# (the module default is true) keep that behavior. Do not add this file unless those
# module arguments are false; two resources cannot own one path.
#
# TODO 1 comes from the connector modules (worklytics-connectors,
# worklytics-connectors-google-workspace, worklytics-connectors-msft-365).
# worklytics-connector-specs supplies the instruction text those modules render;
# it does not emit todo_files itself.
# TODO 2 comes from the host module (test and bulk-setup markdown, plus the API
# load-balancer DNS todo when managed TLS is on).
# TODO 3 comes from the Worklytics connection module.
#
# `moved` blocks cannot be dynamic, and the instance keys do not match
# (module address vs filename), so each file you already have in state needs its
# own block. A `from` address that is not in state is ignored. Uncomment and edit
# the examples at the bottom, or run `terraform state mv`. Without a move, apply
# destroys the in-module file and creates the root one; the file is present again
# at the end of a successful apply.
#
# These local_file resources are deprecated and will be removed in 0.8.
# ./generate-todos.sh writes the same markdown from the todo_files output.

locals {
  todo_files = merge(concat(
    [{}],
    [module.worklytics_connectors.todo_files],
    [module.worklytics_connectors_google_workspace.todo_files],
    [module.worklytics_connectors_msft_365.todo_files],
    [module.psoxy.todo_files],
    [for connection in values(module.connection_in_worklytics) : connection.todo_files],
  )...)
}

resource "local_file" "todo" {
  for_each = var.todos_as_local_files ? local.todo_files : {}

  filename = each.key
  content  = each.value
}

resource "local_file" "test_script" {
  for_each = var.todos_as_local_files ? module.psoxy.test_script_files : {}

  filename        = each.key
  file_permission = "755"
  content         = each.value
}

# moved {
#   from = module.worklytics_connectors.module.source_token_external_todo["asana"].local_file.source_connection_instructions[0]
#   to   = local_file.todo["TODO 1 - setup asana.md"]
# }
# moved {
#   from = module.worklytics_connectors_google_workspace.module.google_workspace_connection["gcal"].local_file.todo_auth_google_workspace[0]
#   to   = local_file.todo["TODO 1 - set up gcal.md"]
# }
# moved {
#   from = module.worklytics_connectors_msft_365.module.msft_365_grants["msft-calendar"].local_file.todo[0]
#   to   = local_file.todo["TODO 1 - setup msft-calendar.md"]
# }
# moved {
#   from = module.worklytics_connectors_msft_365.local_file.todo-with-external-todo["msft-teams"]
#   to   = local_file.todo["TODO 1 - setup msft-teams.md"]
# }
# moved {
#   from = module.psoxy.module.api_connector["asana"].local_file.review[0]
#   to   = local_file.todo["TODO 2 - test asana.md"]
# }
# moved {
#   from = module.psoxy.module.api_connector["asana"].local_file.test_script[0]
#   to   = local_file.test_script["test-asana.sh"]
# }
# moved {
#   from = module.psoxy.local_file.test_all_script[0]
#   to   = local_file.test_script["test-all.sh"]
# }
# moved {
#   from = module.connection_in_worklytics["asana"].local_file.todo_worklytics_connection[0]
#   to   = local_file.todo["TODO 3 - connect asana in Worklytics.md"]
# }
