output "key_secret" {
  value = aws_ssm_parameter.value
}

output "key_value" {
  value = google_service_account_key.key.private_key
}


