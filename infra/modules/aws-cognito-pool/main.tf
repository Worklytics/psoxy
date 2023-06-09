resource "aws_cognito_identity_pool" "main" {
  identity_pool_name               = var.name
  developer_provider_name          = var.developer_provider_name
  allow_unauthenticated_identities = false
  allow_classic_flow               = false

}

resource "aws_iam_policy" "cognito_developer_identities" {
  name        = "${aws_cognito_identity_pool.main.identity_pool_name}_CognitoDeveloperIdentity"
  description = "Allow principal to read and lookup developer identities from Cognito Identity: ${aws_cognito_identity_pool.main.id}"

  policy = jsonencode(
    {
      "Version" : "2012-10-17",
      "Statement" : [
        {
          "Action" : [
            "cognito-identity:GetOpenIdTokenForDeveloperIdentity"
          ],
          "Effect" : "Allow",
          "Resource" : aws_cognito_identity_pool.main.arn
        }
      ]
  })

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

output "policy_arn" {
  value = aws_iam_policy.cognito_developer_identities.arn
}

output "pool_id" {
  value = aws_cognito_identity_pool.main.id
}

output "developer_provider_name" {
  value = aws_cognito_identity_pool.main.developer_provider_name
}
