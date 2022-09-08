resource "aws_ssm_parameter" "long_refreshTokens_client_id" {
  name  = "PSOXY_${upper(replace(var.connector_id, "-", "_"))}_CLIENT_ID"
  type  = "SecureString"
  value = sensitive("TODO: fill me with a real client id!! (via AWS console)")

  lifecycle {
    ignore_changes = [
      value # we expect this to be filled via Console, so don't want to overwrite it with the dummy value if changed
    ]
  }
}

resource "aws_ssm_parameter" "long_refreshTokens_client_secret" {
  name  = "PSOXY_${upper(replace(var.connector_id, "-", "_"))}_CLIENT_SECRET"
  type  = "SecureString"
  value = sensitive("TODO: fill me with a real token!! (via AWS console)")

  lifecycle {
    ignore_changes = [
      value
    ]
  }
}

resource "aws_ssm_parameter" "long-request-token-secret" {
  name        = "PSOXY_${upper(replace(var.connector_id, "-", "_"))}_REFRESH_TOKEN"
  type        = "SecureString"
  description = "The long lived refresh token for `psoxy-${var.connector_id}`"
  value       = sensitive("TODO: fill me with a real token!! (via AWS console)")

  lifecycle {
    ignore_changes = [
      value # we expect this to be filled via Console, so don't want to overwrite it with the dummy value if changed
    ]
  }
}

resource "aws_ssm_parameter" "long_refreshTokens_refresh_endpoint" {
  name      = "PSOXY_${upper(replace(var.connector_id, "-", "_"))}_REFRESH_ENDPOINT"
  type      = "String"
  overwrite = true
  value     = var.refresh_token_endpoint
}

output "refresh_token" {
  value = aws_ssm_parameter.long-request-token-secret
}