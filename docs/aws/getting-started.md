# AWS - Getting Started

**YMMV : this is a work-in-progress guide on how to use EC2 or other environment  to deploy psoxy to AWS.**


To deploy to AWS, you'll need an AWS account in which to deploy. We recommend you provision one
specifically for use in running Psoxy, as this simplifies security boundaries as well as eventual
cleanup.

## Prerequisites

You must have a IAM Role within the target AWS account with sufficient privileges to (AWS managed role examples linked):
   1. create IAM roles + policies (eg [IAMFullAccess](https://us-east-1.console.aws.amazon.com/iam/home?region=us-east-1#/policies/arn:aws:iam::aws:policy/IAMFullAccess$serviceLevelSummary))
   2. create and update Systems Manager Parameters (eg, [AmazonSSMFullAccess](https://us-east-1.console.aws.amazon.com/iam/home?region=us-east-1#/policies/arn:aws:iam::aws:policy/AmazonSSMFullAccess$serviceLevelSummary) )
   3. create and manage Lambdas (eg [AWSLambda_FullAccess](https://us-east-1.console.aws.amazon.com/iam/home?region=us-east-1#/policies/arn:aws:iam::aws:policy/AWSLambda_FullAccess$serviceLevelSummary) )
   4. create and manage S3 buckets (eg [AmazonS3FullAccess](https://us-east-1.console.aws.amazon.com/iam/home?region=us-east-1#/policies/arn:aws:iam::aws:policy/AmazonS3FullAccess$serviceLevelSummary) )
   5. create Cloud Watch Log groups (eg [CloudWatchFullAccess](https://us-east-1.console.aws.amazon.com/iam/home?region=us-east-1#/policies/arn:aws:iam::aws:policy/CloudWatchFullAccess$serviceLevelSummary))

You must be able to assume that role.

(Yes, the use of AWS Managed Policies results in a role with many privileges; that's why we
recommend you use a dedicated AWS account to host proxy which is NOT shared with any other use case)

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
