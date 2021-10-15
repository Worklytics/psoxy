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

```

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

# Google Chat

# Google Meet


```

```shell
terraform init
terraform apply
```


Then run the shell command produced by Terraform
