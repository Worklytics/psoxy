# AWS Troubleshooting

Tips and tricks for using AWS as to host the proxy.

## Who are you?

```shell
# figure out how your AWS CLI is authenticated
# (NOTE: this is also the only AWS API cmd that will work regardless of your IAM setup; asking AWS
# who it believes you are doesn't require any permissions)
aws sts get-caller-identity

# figure out if the identity you're authenticated as can assume target role
aws sts assume-role \
--role-arn arn:aws:iam::123456789012:role/InfraAdmin \
--role-session-name TestSession \
--output json
```

If above doesn't happen seem to work as expected, some ideas in the next section may help.

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

## Logs via Cloud Watch

### via Web Console

  1. Log into AWS web console
  2. navigate to the AWS account that hosts your proxy instance (you may need to assume a
     role in that account)
  3. then the region in that account in which your proxy instance is deployed.
     (default `us-east-1`)
  4. then search or navigate to the `AWS Lambda`s feature, and find the specific one you
     wish to debug
  5. find the tabs for `Monitoring` then within that, `Logging`, then click "go to Cloud Watch"


### via CLI

Unless your AWS CLI is auth'd as a user who can review logs, first auth it for such a role.

You can do this with a new profile, or setting env variables as follows:

```shell
export $(printf "AWS_ACCESS_KEY_ID=%s AWS_SECRET_ACCESS_KEY=%s AWS_SESSION_TOKEN=%s" \
$(aws sts assume-role \
--role-arn arn:aws:iam::123456789012:role/MyAssumedRole \
--role-session-name MySessionName \
--query "Credentials.[AccessKeyId,SecretAccessKey,SessionToken]" \
--output text))
````

Then, you can do a series of commands as follows:
```shell
aws logs describe-log-streams --log-group-name /aws/lambda/psoxy-azure-ad
aws logs get-log events --log-group-name /aws/lambda/psoxy-azure-ad --log-stream-name [VALUE_FROM_LAST_COMMAND]
```

