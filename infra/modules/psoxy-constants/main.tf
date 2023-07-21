locals {

  # AWS Managed polices
  # see: https://docs.aws.amazon.com/IAM/latest/UserGuide/access_policies_managed-vs-inline.html#aws-managed-policies
  required_aws_roles_to_provision_host = {
    "arn:aws:iam::aws:policy/IAMFullAccess"        = "IAMFullAccess"
    "arn:aws:iam::aws:policy/AmazonS3FullAccess"   = "AmazonS3FullAccess"
    "arn:aws:iam::aws:policy/CloudWatchFullAccess" = "CloudWatchFullAccess"
    "arn:aws:iam::aws:policy/AmazonSSMFullAccess"  = "AmazonSSMFullAccess"
    "arn:aws:iam::aws:policy/AWSLambda_FullAccess" = "AWSLambda_FullAccess"
  }
  # AWS managed policy required to consume Microsoft 365 data
  # (in addition to above)
  required_aws_managed_policies_to_consume_msft_365_source = {
    "arn:aws:iam::aws:policy/AmazonCognitoPowerUser" = "AmazonCognitoPowerUser"
  }

  # TODO: create IAM policy document, which installer could use to create their own policy as
  # alternative to using AWS Managed policies

  # initial GCP APIs that must be enabled in projects that will host the proxy.
  # (Terraform apply will enabled additional ones)
  required_gcp_apis_to_host = {
    # https://console.cloud.google.com/apis/library/iamcredentials.googleapis.com
    "iamcredentials.googleapis.com" = "IAM Service Account Credentials API",
    # https://console.cloud.google.com/apis/library/serviceusage.googleapis.com
    "serviceusage.googleapis.com"   = "Service Usage API",
  }

  required_gcp_roles_to_provision_host = {
    "roles/storage.admin" = {
      display_name    = "Storage Admin",
      description_url = "https://cloud.google.com/iam/docs/understanding-roles#storage.admin"
    },
    "roles/iam.roleAdmin" = {
      display_name    = "IAM Role Admin",
      description_url = "https://cloud.google.com/iam/docs/understanding-roles#iam.roleAdmin"
    },
    "roles/secretmanager.admin" = {
      display_name    = "Secret Manager Admin",
      description_url = "https://cloud.google.com/iam/docs/understanding-roles#secretmanager.admin"
    },
    "roles/iam.serviceAccountAdmin" = {
      display_name    = "Service Account Admin",
      description_url = "https://cloud.google.com/iam/docs/understanding-roles#iam.serviceAccountAdmin"
    },
    "roles/serviceusage.serviceUsageAdmin" = {
      display_name    = "Service Usage Admin",
      description_url = "https://cloud.google.com/iam/docs/understanding-roles#serviceusage.serviceUsageAdmin"
    },
    "roles/cloudfunctions.admin" = {
      display_name    = "Cloud Functions Admin",
      description_url = "https://cloud.google.com/iam/docs/understanding-roles#cloudfunctions.admin"
    },
  }
  # TODO: add list of permissions, which customer could use to create custom role as alternative


  # TODO: confirm that this is indeed the same list (believe it is)
  required_gcp_apis_to_provision_google_workspace_source = local.required_gcp_apis_to_host

  required_gcp_roles_to_provision_google_workspace_source = {
    "roles/iam.serviceAccountAdmin" = {
      display_name    = "Service Account Admin",
      description_url = "https://cloud.google.com/iam/docs/understanding-roles#iam.serviceAccountAdmin"
    },
    "roles/serviceusage.serviceUsageAdmin" = {
      display_name    = "Service Usage Admin",
      description_url = "https://cloud.google.com/iam/docs/understanding-roles#serviceusage.serviceUsageAdmin"
    }
  }
  # TODO: add list of permissions, which customer could use to create custom role as alternative

  required_azuread_roles_to_provision_msft_365_source = {
    "7ab1d382-f21e-4acd-a863-ba3e13f7da61" = "Cloud Application Administrator",
  }
}
