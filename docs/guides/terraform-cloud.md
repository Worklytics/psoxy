# Terraform Cloud / Enterprise

If you're using Terraform Cloud or Enterprise, here are a few things to keep in mind.

NOTE: this is tested only for gcp; for aws YMMV, and in particular we expect Microsoft 365 sources will not work properly, given how those are authenticated.

## Getting Started

Prereqs:

- git/java/maven, as described here https://github.com/Worklytics/psoxy#required-software-and-permissions
- for testing, you'll need the CLI of your host environment (eg, AWS CLI, GCloud CLI, Azure CLI) as well as npm/NodeJS installed on your local machine

After authenticating your terraform CLI to Terraform Cloud/enterprise, you'll need to:

1. Create a Project in Terraform Cloud; and a workspace within the project.
2. Clone one of our example repos and run the `./init` script to initialize your `terraform.tfvars` for Terraform Cloud. This will also put a bunch of useful tooling on your machine.

```shell
./init
```

    Follow prompts to select that you don't wish to run `terraform apply` locally, and that you wish to use Terraform Cloud.

3. Commit the bundle that was output by the `./init` script to your repo:

```shell
git add psoxy-*
git commit -m "commit of built bundle"
```

4. Change the terraform backend `main.tf` to point to your Terraform Cloud rather than be local
   - remove `backend` block from `main.tf`
   - add a `cloud` block within the `terraform` block in `main.tf` (obtain content from your Terraform Cloud)

5. run `terraform init` to migrate the initial "local" state to the remote state in Terraform Cloud

6. You'll have to authenticate your Terraform Cloud with Google / AWS / Azure, depending on the cloud you're deploying to / data sources you're using.

## TODOs as Outputs

If you're using Terraform Cloud or Enterprise, our convention of writing "TODOs" to the local file system might not work for you.

To address this, we've updated most of our examples to also output todo values as Terraform outputs, `todos_1`, `todos_2`, etc.

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

If you have `terraform` CLI auth'd against your Terraform Cloud or Enterprise instance, then you might be able to avoid the curl-hackery above, and instead use the following:

```shell
terraform output -raw todos_1 > todos_1.md
terraform output -raw todos_2 > todos_2.md
terraform output -raw todos_3 > todos_3.md
```

(This approach should also work with Terraform CLI running with `backend`, rather than `cloud`)

## Testing Locally

As Terraform Cloud runs remotely, the test tool we provide for testing your deployment will not be available by default on your local machine. You can install it locally and adapt the suggestions from the `todos_2` output variable of your terraform run to test your deployment from your local machine or another environment. See [testing.md](testing.md) for details.

If you have run our `init` script locally (as suggested in 'Getting Started') then the test tool _should_ have been installed (likely at `.terraform/modules/psoxy/tools/`). You will need to update everything in `todos_2.md` to point to this path for those test commands to work.

If you need to directly install/re-install it, something like the following should work:

```shell
.terraform/modules/psoxy/tools/install-test-tool.sh .terraform/modules/psoxy/tools/
```
