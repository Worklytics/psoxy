# AWS - Getting Started

**YMMV : this is a work-in-progress guide on how to use EC2 or other environment  to deploy psoxy to AWS.**

If your organization prefers NOT to authorize the AWS CLI on individual laptops and/or outside AWS,
provisioning Psoxy's required infra from an EC2 instance may be an option.

## Authorization

### Your Local Machine or a VM/Container Outside AWS

  1. [Generate an AWS Access Key](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_access-keys.html) for your AWS User.
  2. Run `aws configure` in a terminal on the machine you plan to use, and configure it with the key
     you generated in step one.

### EC2 Instance
  1. provision an EC2 instance (or request that your IT/dev ops team provision one for you). We
     recommend a micro instance with an 8GB disk, running `ubuntu` (not Amazon Linux; if you
     choose something else, you may need to adapt these instructions. Be sure to create a PEM key
     to access it via SSH.
  2. [connect to your instance](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/AccessingInstances.html?icmpid=docs_ec2_console),


```shell
# avoid ssh complaints about permissions on your key
chmod 400 psoxy-access-key.pem

ssh -i ~/psoxy-access-key.pem ubuntu@{PUBLIC_IPV4_DNS_OF_YOUR_EC2_INSTANCE}
```

Follow general [prereq installation](../prereqs-ubuntu.md), and, when ready, continue with [README](../../README.md).
