
# legacy migration
moved {
  from = local_file.todo-google-workspace-admin-console
  to   = local_file.todo_auth_google_workspace[0]
}

# 0_4_36 migration
moved {
  from = google_service_account.connector-sa
  to   = google_service_account.connector_sa
}

