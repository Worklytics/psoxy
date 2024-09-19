# psoxy-constants

Provider-less module that defines a bunch of constants that you might need to provision Psoxy, or
bootstrap stuff needed for provisioning.

## Usage

Use this to bootstrap roles that a service account needs to provision Psoxy in GCP:


### Grant Roles to a GCP service account

```hcl
module "psoxy_constants" {
  source = "git::https://github.com/worklytics/psoxy//infra/modules/psoxy-constants?ref=v0.4.27"
}

resource "google_project_iam_member" "perms" {
    for_each = module.psoxy_constants.required_gcp_roles_to_provision_host

    # pick one of these options for member
    member  = "serviceAccount:{{YOUR_SERVICE_ACCOUNT_EMAIL_ADDRESS}}}"
    # member = "user:{{YOUR_GCP_USER_EMAIL_ADDRESS}}"
    # member = "group:{{YOUR_GCP_GROUP_EMAIL_ADDRESS}}"
    project = "{{YOUR_GCP_PROJECT_ID}}"
    role    = each.key
}
```

### Attach Required Predefined AWS Policies to an AWS IAM Role

equivalent of https://docs.worklytics.co/psoxy/aws/getting-started#prerequisites step 2

```hcl
module "psoxy_constants" {
  source = "git::https://github.com/worklytics/psoxy//infra/modules/psoxy-constants?ref=v0.4.61"
}

resource "aws_iam_role_policy_attachment" "roles_for_psoxy_deploy_to_github_actions" {
    for_each = module.psoxy_constants.required_aws_roles_to_provision_host

    role       = "{{NAME_OF_YOUR_AWS_ROLE}}"
    policy_arn = each.key
}
```

### Create a Least-Privileged AWS IAM Role for Provisioning

```hcl
module "psoxy_constants" {
  source = "git::https://github.com/worklytics/psoxy//infra/modules/psoxy-constants?ref=v0.4.61"
}

resource "aws_iam_policy" "min_provisioner_policy" {
    name   = "PsoxyMinProvisioner"
    policy = module.psoxy_constants.aws_least_privileged_policy
}

resource "aws_iam_role_policy_attachment" "min_provisioner_policy" {
    policy_arn         = aws_iam_policy.min_provisioner_policy.arn
    role               = "{{NAME_OF_YOUR_AWS_PROVISIONER_ROLE}}"
}
```

