# psoxy-constants

Provider-less module that defines a bunch of constants that you might need to provision Psoxy, or
bootstrap stuff needed for provisioning.

## Usage

Use this to bootstrap roles that a service account needs to provision Psoxy in GCP:

```hcl
module "psoxy_constants" {
  source = "git::https://github.com/worklytics/psoxy//infra/modules/psoxy-constants?ref=v0.4.26"
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
