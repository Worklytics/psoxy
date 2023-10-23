
# support refactoring output bucket into distinct module
moved {
  from = google_storage_bucket.output-bucket
  to   = module.output_bucket.google_storage_bucket.bucket
}
