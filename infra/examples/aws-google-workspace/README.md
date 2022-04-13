# AWS

Example Terraform configuration for deploying psoxy in AWS and connecting to Google Workspace sources

Example `terraform.tfvars`:
```terraform
aws_account_id                = "123456789" # your AWS account in which to provision resources
aws_assume_role_arn           = "arn:aws:iam::123456789:role/InfraAdmin" # role Terraform should assume when provisioning resources
allowed_gcp_service_accounts  = [] # add SA id you get from Worklytics here
gcp_folder_id                 = null # optional ID of folder in which to put GCP project that will hold service accounts used to connect to Google Workspace
gcp_billing_account_id        = "123456-789ABC-DEF012" # billing account ID in which to create GCP project
gcp_project_id                = "psoxy-dev-aws-example-12314332" # project ID to get to GCP project
```

NOTE: if using existing gcp project, with id `my-existing-project`, you can use `terraform import`
to avoid creating a new one. Do this BEFORE you run `terraform apply`.

```shell
terraform import google_project.psoxy-google-connectors my-existing-project
```

