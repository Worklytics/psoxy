# Terraform Cloud / Enterprise

If you're using Terraform Cloud or Enterprise, here are a few things to keep in mind.


## TODOs as Outputs
If you're using Terraform Cloud or Enterprise, our convention of writing "TODOs" to the local file
system might not work for you.

To address this, we've updated most of our examples to also output todo values as Terraform
outputs, `todos_1`, `todos_2`, etc.

To get them nicely on your local machine, something like the following:

### Terraform API
  1. get an API token from your Terraform Cloud or Enterprise instance (eg, https://developer.hashicorp.com/terraform/cloud-docs/users-teams-organizations/api-tokens).

  2. set it as an env variable, as well as the host:
```shell
export TF_TOKEN={YOUR_TOKEN}
export TF_HOST={YOUR_TERRAFORM_HOST} # eg, app.terraform.io
export TF_WORKSPACE_ID={YOUR_TERRAFORM_WORKSPACE_ID} #eg, something like ws-1234567890
```

  3. run a curl command using those values to get each todos:

```shell
 curl --header "Authorization: Bearer $TF_TOKEN" \
  --header "Content-Type: application/vnd.api+json" \
  "https://${TF_HOST}/api/v2/workspaces/${TF_WORKSPACE_ID}/current-state-version?include=outputs" \
    | jq -r '.included[].attributes | select(.name == "todos_1") | .value | join("\n")' > todos_1.md

curl --header "Authorization: Bearer $TF_TOKEN" \
  --header "Content-Type: application/vnd.api+json" \
  "https://${TF_HOST}/api/v2/workspaces/${TF_WORKSPACE_ID}/current-state-version?include=outputs" \
    | jq -r '.included[].attributes | select(.name == "todos_2") | .value | join("\n")' > todos_2.md

curl --header "Authorization: Bearer $TF_TOKEN" \
  --header "Content-Type: application/vnd.api+json" \
  "https://${TF_HOST}/api/v2/workspaces/${TF_WORKSPACE_ID}/current-state-version?include=outputs" \
    | jq -r '.included[].attributes | select(.name == "todos_3") | .value | join("\n")' > todos_3.md
```

### Terraform CLI

If you have `terraform` CLI auth'd against your Terraform Cloud or Enterprise instance, then
you might be able to avoid the curl-hackery above, and instead use the following:

```shell
terraform output -raw todos_1 > todos_1.md
terraform output -raw todos_2 > todos_2.md
terraform output -raw todos_3 > todos_3.md
```

(This approach should also work with Terraform CLI running with `backend`, rather than `cloud`)
