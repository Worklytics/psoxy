# AWS - Getting Started

## Prerequisites

1. **An AWS Account in which to deploy Psoxy** We *strongly* recommend you provision one specifically
   for use to host Psoxy, as this will create  an implicit security boundary, reduce possible
   conflicts with other infra configured in the account, and simplify eventual cleanup.

   You will need the numeric AWS Account ID for this account, which you can find in the AWS Console.


2. **A sufficiently privileged AWS Role** You must have a IAM Role within the AWS account with
  sufficient privileges to (AWS managed role examples linked):
       1. create IAM roles + policies (eg [IAMFullAccess](https://us-east-1.console.aws.amazon.com/iam/home?region=us-east-1#/policies/arn:aws:iam::aws:policy/IAMFullAccess$serviceLevelSummary))
       2. create and update Systems Manager Parameters (eg, [AmazonSSMFullAccess](https://us-east-1.console.aws.amazon.com/iam/home?region=us-east-1#/policies/arn:aws:iam::aws:policy/AmazonSSMFullAccess$serviceLevelSummary) )
       3. create and manage Lambdas (eg [AWSLambda_FullAccess](https://us-east-1.console.aws.amazon.com/iam/home?region=us-east-1#/policies/arn:aws:iam::aws:policy/AWSLambda_FullAccess$serviceLevelSummary) )
       4. create and manage S3 buckets (eg [AmazonS3FullAccess](https://us-east-1.console.aws.amazon.com/iam/home?region=us-east-1#/policies/arn:aws:iam::aws:policy/AmazonS3FullAccess$serviceLevelSummary) )
       5. create Cloud Watch Log groups (eg [CloudWatchFullAccess](https://us-east-1.console.aws.amazon.com/iam/home?region=us-east-1#/policies/arn:aws:iam::aws:policy/CloudWatchFullAccess$serviceLevelSummary))

    (Yes, the use of AWS Managed Policies results in a role with many privileges; that's why we
    recommend you use a dedicated AWS account to host proxy which is NOT shared with any other use case)

    You will need the ARN of this role.

3. **An authenticated AWS CLI in your provisioning environment**. Your environment (eg, shell/etc
   from which you'll run terraform commands) must be authenticated as an identity that can assume
   that role. (see next section for tips on options for various environments you can use)

    Eg, if your Role is `arn:aws:iam::123456789012:role/PsoxyProvisioningRole`, the following
    should work:

```shell
aws sts assume-role --role-arn arn:aws:iam::123456789012:role/PsoxyProvisioningRole --role-session-name tf_session
```

    If not, use `aws sts get-caller-identity` to confirm how your CLI is authenticated.

## Provisioning Environment

To provision AWS infra, you'll need the `aws-cli` installed and authenticated on the environment
where you'll run `terraform`.

Here are a few options:

### Your Local Machine or a VM/Container Outside AWS

  1. [Generate an AWS Access Key](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_access-keys.html) for your AWS User.
  2. Run `aws configure` in a terminal on the machine you plan to use, and configure it with the key
     you generated in step one.

NOTE: this could even be a [GCP Cloud Shell](https://cloud.google.com/shell), which may simplify
auth if your wish to connect your Psoxy instance to Google Workspace as a data source.


### EC2 Instance
If your organization prefers NOT to authorize the AWS CLI on individual laptops and/or outside AWS,
provisioning Psoxy's required infra from an EC2 instance may be an option.

  1. provision an EC2 instance (or request that your IT/dev ops team provision one for you). We
     recommend a micro instance with an 8GB disk, running `ubuntu` (not Amazon Linux; if you
     choose that or something else, you may need to adapt these instructions). Be sure to create a
     PEM key to access it via SSH (unless your AWS Organization/account provides some other ssh solution).
  2. associate the Role above with your instance (see https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_use_switch-role-ec2.html)
  3. [connect to your instance](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/AccessingInstances.html?icmpid=docs_ec2_console),


```shell
# avoid ssh complaints about permissions on your key
chmod 400 psoxy-access-key.pem

ssh -i ~/psoxy-access-key.pem ubuntu@{PUBLIC_IPV4_DNS_OF_YOUR_EC2_INSTANCE}
```

Whichever environment you choose, follow general [prereq installation](../prereqs-ubuntu.md), and,
when ready, continue with [README](../../README.md).


## Terraform State Backend

You'll also need a backend location for your Terraform state (such as an S3 bucket). It can be in
any AWS account, as long as the AWS role that you'll use to run Terraform has read/write access to
it.

Alternatively, you may use a local file system, but this is not recommended for production use - as
your Terraform state may contain secrets such as API keys, depending on the sources you connect.
