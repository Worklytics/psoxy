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

### Grant optional roles for external Application Load Balancer (GCP)

When `external_api_alb` is set on `gcp-host` (not for BYO `api_connector_external_lb_host`):

```hcl
resource "google_project_iam_member" "external_api_alb" {
  for_each = module.psoxy_constants.required_gcp_roles_to_use_external_api_alb

  member  = "serviceAccount:{{YOUR_SERVICE_ACCOUNT_EMAIL_ADDRESS}}"
  project = "{{YOUR_GCP_PROJECT_ID}}"
  role    = each.key
}
```

### Grant roles to provision Google Workspace connectors

Apply these on the GCP project that hosts the Domain-wide Delegation service accounts (often a dedicated project, not necessarily the proxy host project). Base roles are always required; add the key or WIF extras depending on `google_workspace_connector_settings.api_client_auth_method` passed to `worklytics-connectors-google-workspace`.

```hcl
module "psoxy_constants" {
  source = "git::https://github.com/worklytics/psoxy//infra/modules/psoxy-constants?ref=v0.7.1"
}

# Always: create DWD service accounts and enable APIs (also covers binding Token Creator / Workload Identity User).
resource "google_project_iam_member" "gws" {
  for_each = module.psoxy_constants.required_gcp_roles_to_provision_google_workspace_source

  member  = "serviceAccount:{{YOUR_SERVICE_ACCOUNT_EMAIL_ADDRESS}}"
  project = "{{YOUR_GWS_GCP_PROJECT_ID}}"
  role    = each.key
}

# Default path (api_client_auth_method = service_account_key): downloaded JSON keys.
# Omit this (and you may revoke Service Account Key Admin) when using workload_identity_federation.
resource "google_project_iam_member" "gws_sa_keys" {
  for_each = module.psoxy_constants.required_gcp_roles_to_provision_google_workspace_source_with_sa_keys

  member  = "serviceAccount:{{YOUR_SERVICE_ACCOUNT_EMAIL_ADDRESS}}"
  project = "{{YOUR_GWS_GCP_PROJECT_ID}}"
  role    = each.key
}

# AWS host + api_client_auth_method = workload_identity_federation: WIF pool + AWS provider.
# Not required on GCP hosts (no pool). Token Creator is granted to the proxy runtime, not this runner.
resource "google_project_iam_member" "gws_wif" {
  for_each = module.psoxy_constants.required_gcp_roles_to_provision_google_workspace_source_with_wif

  member  = "serviceAccount:{{YOUR_SERVICE_ACCOUNT_EMAIL_ADDRESS}}"
  project = "{{YOUR_GWS_GCP_PROJECT_ID}}"
  role    = each.key
}
```

Custom-role permission lists: `required_gcp_permissions_to_provision_google_workspace_source_base` plus either `required_gcp_permissions_to_provision_google_workspace_source` (keys) or `required_gcp_permissions_to_provision_google_workspace_source_with_wif` (AWS WIF). See [Google Workspace](../../../docs/sources/google-workspace/README.md).

### Attach min set of AWS-Managed Policies to an AWS IAM Role for Provisioning

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

equivalent of https://docs.worklytics.co/psoxy/aws/getting-started#prerequisites step 2

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

