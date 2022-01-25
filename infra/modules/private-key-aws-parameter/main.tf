resource "aws_ssm_parameter" "private-key" {
  name        = "PSOXY_${var.instance_id}_PRIVATE_KEY"
  type        = "SecureString"
  description = "Value of private key"
  value       = var.private_key
}

resource "aws_ssm_parameter" "private-key-id" {
  name        = "PSOXY_${var.instance_id}_PRIVATE_KEY_ID"
  type        = "SecureString" # probably not necessary
  description = "ID of private key"
  value       = var.private_key_id
}

output "parameters" {
  value = [
    aws_ssm_parameter.private-key-id,
    aws_ssm_parameter.private-key
  ]
}
