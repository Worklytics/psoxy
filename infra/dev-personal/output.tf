output "gmail_connector_sa_email" {
  value = module.gmail-connector.service_account_email
}

output "google_chat_connector_sa_email" {
  value = module.google-chat-connector.service_account_email
}
