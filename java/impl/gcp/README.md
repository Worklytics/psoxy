# README


## Deployment

Prereqs:
  - `terraform` - install terraform
  - `mvn`  - install version 3 of Maven on your machine
  - `java` - you need some flavor of Java 11 JDK to compile
  - `gcloud` - install latest version of GCloud cmd line tools and authenticate (`gcloud auth login`)

Presumes that you've already invoked Terraform to provision the GCP environment that will host the
function.

### Provision GCP Environment
Write a Terraform config for your environment. We've provided some modules to make this concise, so
something like the following will work:

```terraform
terraform {
  required_providers {
    google = {
      version = "~> 3.74.0"
    }
  }

  # if you leave this as local, you should backup/commit your TF state files
  backend "local" {
  }
}

# NOTE: if you don't have perms to provision a GCP project in your billing account, you can have
# someone else create one and than import it:
#  `terraform import google_project.psoxy-project your-psoxy-project-id`
# either way, we recommend the project be used exclusively to host psoxy instances corresponding to
# a single worklytics account
resource "google_project" "psoxy-project" {
  name            = "Psoxy - ${var.environment_name}"
  project_id      = var.project_id
  folder_id       = var.folder_id
  billing_account = var.billing_account_id
}

module "psoxy-gcp" {
  source = "../modules/gcp"

  project_id          = google_project.psoxy-project.project_id
  invoker_sa_emails   = var.worklytics_sa_emails

  depends_on = [
    google_project.psoxy-project
  ]
}
```
Then init and apply that:

```shell
terraform init
terraform apply
```


### Build Psoxy for GCP

From the source checkout, build a JAR within the `gcp` directory:

```shell
cd java/impl/gcp/
mvn package
```

### Provision Google Workspace Connectors
Optional; this is needed only if you're connecting to Google Workspace data sources via Psoxy.

It will be simplest to add terraform code, like the examples below, to the Terraform configuration
you wrote above.
```terraform
# GMail
module "gmail-connector" {
  source = "../modules/google-workspace-dwd-connection"

  project_id                   = var.project_id
  connector_service_account_id = "psoxy-gmail-dwd"
  display_name                 = "Psoxy Connector - GMail"
  apis_consumed                = [
    "gmail.googleapis.com"
  ]

  depends_on = [
    module.psoxy-gcp
  ]
}
```

```shell
terraform init
terraform apply
```


Then run the shell command produced by Terraform.
