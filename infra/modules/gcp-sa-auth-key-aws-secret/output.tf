output "key_secret_name" {
  value = aws_secretsmanager_secret.service-account-key.name
}

output "key_secret_version_id" {
  value =  aws_secretsmanager_secret_version.service-account-key-version.version_id
}

output "key_value" {
  value = google_service_account_key.key.private_key
}


