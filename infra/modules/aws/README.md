# AWS

This Terraform module sets up an AWS account to host psoxy instances.

We recommend creating a fresh AWS account with an IAM Role. Auth your AWS CLI on your machine as an
AWS User authorized to assume that Role, and configure Terraform AWS provider to assume it.

Your AWS User should use MFA. To manage this with CLI, you can use a script such as [aws-mfa](https://github.com/broamski/aws-mfa)
to get short-lived key+secret for your user.



