# AWS Trouble Shooting

Tips and tricks for using AWS as to host the proxy.

## Your AWS Organization uses SSO via Okta or some similar provider

Options:
  1. execute terraform via [AWS Cloud Shell](cloud-shell.md)
  2. find credentials output by your SSO helper (eg, `aws-okta`) then fill the AWS CLI env variables yourself:

```shell
ls ~/.aws/cli/cached/

...

export AWS_ACCESS_KEY_ID="xxxxxxxxxxxxxxx"
export AWS_SECRET_ACCESS_KEY="xxxxxxxxxxxxxxx"
export AWS_SESSION_TOKEN="xxxxxxxxxxxxxxx"
```

  3. if your SSO helper fills default AWS credentials file but simply doesn't set the env vars, you
 may be able to export the profile to `AWS_PROFILE`, eg
```shell

export AWS_PROFILE="production"
terraform plan

# or just inline

AWS_PROFILE="production" terraform plan
```

References:
https://discuss.hashicorp.com/t/using-credential-created-by-aws-sso-for-terraform/23075/7


## Your AWS User has MFA

Options:
  - execute terraform via [AWS Cloud Shell](cloud-shell.md)
  - use a script such as [aws-mfa](https://github.com/broamski/aws-mfa) to get short-lived key+secret for your user.
