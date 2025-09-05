
moved {
  from = google_service_account.webhook_batch_invoker
  to   = google_service_account.webhook_batch_invoker[0]
}