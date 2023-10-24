
# moves that were defined as of 0.4.39, without reference to when first added
moved {
  from = local_file.todo-gcp-psoxy-bulk-test
  to   = local_file.todo_test_gcp_psoxy_bulk[0]
}

moved {
  from = local_file.test_script
  to   = local_file.test_script[0]
}

# added in 0.4.40 for consistent style
moved {
  from = google_storage_bucket.input-bucket
  to   = google_storage_bucket.input_bucket
}

moved {
  from = google_project_service.gcp-infra-api
  to   = google_project_service.gcp_infra_api
}
