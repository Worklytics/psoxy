# provisions User, key, AWS IAM Policy needed for AWS IAM Auth method in Vault; and the vault backend
# **ALPHA support**

# AWS IAM user for Vault
resource "aws_iam_user" "vault" {
  name = "VaultAwsAuth"
}

# from https://developer.hashicorp.com/vault/docs/auth/aws#recommended-vault-iam-policy
resource "aws_iam_user_policy" "vault" {
  name = "VaultAwsAuthPolicy"
  user = aws_iam_user.vault.name

  policy = jsonencode(
    {
      "Version" : "2012-10-17",
      "Statement" : [
        {
          "Effect" : "Allow",
          "Action" : [
            "ec2:DescribeInstances",
            "iam:GetInstanceProfile",
            "iam:GetUser",
            "iam:GetRole"
          ],
          "Resource" : "*"
        },
        {
          "Effect" : "Allow",
          "Action" : [
            "sts:AssumeRole"
          ],
          "Resource" : [
            "arn:aws:iam::874171213677:role/VaultRole"
          ]
        },
        {
          "Sid" : "ManageOwnAccessKeys",
          "Effect" : "Allow",
          "Action" : [
            "iam:CreateAccessKey",
            "iam:DeleteAccessKey",
            "iam:GetAccessKeyLastUsed",
            "iam:GetUser",
            "iam:ListAccessKeys",
            "iam:UpdateAccessKey"
          ],
          "Resource" : "arn:aws:iam::*:user/$${aws:username}"
        }
      ]
    }
  )
}

resource "aws_iam_access_key" "for_vault" {
  user = aws_iam_user.vault.name
}

resource "vault_auth_backend" "aws" {
  type = "aws"

  tune {
    max_lease_ttl = "${24 * 3600}s" # one day
  }
}

resource "vault_aws_auth_backend_client" "aws" {
  backend    = vault_auth_backend.aws.path
  access_key = aws_iam_access_key.for_vault.id
  secret_key = aws_iam_access_key.for_vault.secret
}

# use this to create role for each Psoxy instance to access Vault
# (so can scope perms to just what that instance needs to read/write in Vault)
output "vault_aws_auth_backend_path" {
  value = vault_auth_backend.aws.path
}




