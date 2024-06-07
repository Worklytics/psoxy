# Least Privileged Provisioning Role

**alpha** - *as of v0.4.55, this is not fully tested*

This is a guide about how to create a role for provisioning psoxy infrastructure in AWS, following
the principle of least-privilege at permission-level, rather than policy-level.

Eg, as of v0.4.55 of the proxy, our docs provide guidance on using an AWS role to provision your
psoxy infrastructure using the least-privileged set of AWS managed policies possible. A stronger
standard would be to use a custom IAM policy rather than AWS managed policy, with the
least-privileged set of *permissions* required.

Additionally, you can specify resource constraints to improve security within a shared AWS account.
(However, we do not recommend or officially support deployment into a shared AWS account. We
recommend deploying your proxy instances in isolated AWS account to provide an implicit security
boundary by default, as an additional layer of protection beyond those provided by our proxy modules)


We provide an example IAM policy document in our `psoxy-constants` module that you can use to create
a IAM policy in AWS.  You can do this outside terraform, finding the JSON from that policy OR
via terraform as follows:

```hcl
module "psoxy_constants" {
  source = "git::https://github.com/worklytics/psoxy//infra/modules/psoxy-constants?ref=v0.4.55"
  environment_name = "psoxy" # use some qualifier here, to prefix your infra within shared account avoiding collisions
}

resource "aws_iam_role" "min_provisioner" {
    name = "MinProvisioner"
    assume_role_policy = jsonencode({
        "Version": "2012-10-17",
        "Statement": [
            {
                "Effect": "Allow",
                "Principal": {
                    # add AWS user of anyone who will need to be able to assume the role to provision infra
                    # "AWS": "arn:aws:iam::01234567789012:user/erik"
                },
                "Action": "sts:AssumeRole"
            }
        ]
    })
}

resource "aws_iam_policy" "min_provisioner_policy" {
    name   = "MinProvisioner"
    policy = module.psoxy_constants.aws_least_privileged_policy
}

resource "aws_iam_role_policy_attachment" "min_provisioner_policy" {
    policy_arn         = aws_iam_policy.min_provisioner_policy.arn
    role               = aws_iam_role.min_provisioner.name
}
```
