output "key_secret_name" {
  value = aws_ssm_parameter.value.name
}

output "key_value" {
  value = google_service_account_key.key.private_key
}


