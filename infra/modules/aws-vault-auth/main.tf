# sets up User + Policy needed for AWS IAM Auth method in Vault

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
      "Version": "2012-10-17",
      "Statement": [
        {
          "Effect": "Allow",
          "Action": [
            "ec2:DescribeInstances",
            "iam:GetInstanceProfile",
            "iam:GetUser",
            "iam:GetRole"
          ],
          "Resource": "*"
        },
        {
          "Effect": "Allow",
          "Action": [
            "sts:AssumeRole"
          ],
          "Resource": [
            "arn:aws:iam::874171213677:role/VaultRole"
          ]
        },
        {
          "Sid": "ManageOwnAccessKeys",
          "Effect": "Allow",
          "Action": [
            "iam:CreateAccessKey",
            "iam:DeleteAccessKey",
            "iam:GetAccessKeyLastUsed",
            "iam:GetUser",
            "iam:ListAccessKeys",
            "iam:UpdateAccessKey"
          ],
          "Resource": "arn:aws:iam::*:user/$${aws:username}"
        }
      ]
    }
  )
}

resource "local_file" "todo-aws_auth_vault" {
  filename = "TODO - setup AWS Auth in Vault.md"
  content = <<EOT

  1. create AWS secret key for ${aws_iam_user.vault.name}
  2. enable AWS auth in your Vault server; set key id/value to what you just created
EOT
}
