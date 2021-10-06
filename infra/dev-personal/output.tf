
# current convention is that the Cloud Functions for each of these run as these SAs, but there's
# really no need to; consider splitting (eg, one SA for each Cloud Function; one SA for the
# OAuth client)

output "gmail_connector_sa_email" {
  value = module.gmail-connector.service_account_email
}

output "google_chat_connector_sa_email" {
  value = module.google-chat-connector.service_account_email
}
