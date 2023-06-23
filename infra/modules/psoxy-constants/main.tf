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
  # TODO: create IAM policy document, which customer could use to create their own policy as
  # alternative to using AWS Managed policies

  required_gcp_roles_to_provision_host = {
    "roles/iam.serviceAccountAdmin" = {
      display_name    = "Service Account Admin",
      description_url = "https://cloud.google.com/iam/docs/understanding-roles#iam.serviceAccountAdmin"
    },
    "roles/serviceusage.serviceUsageAdmin" = {
      display_name    = "Service Usage Admin",
      description_url = "https://cloud.google.com/iam/docs/understanding-roles#serviceusage.serviceUsageAdmin"
    },
  }  # TODO: add list of permissions, which customer could use to create custom role as alternative

  required_gcp_roles_to_provision_google_workspace_source = {
    "roles/storage.admin" = {
      display_name    = "Storage Admin",
      description_url = "https://cloud.google.com/iam/docs/understanding-roles#storage.admin"
    },
    "roles/iam.roles.admin" = {
      display_name    = "IAM Roles Admin",
      description_url = "https://cloud.google.com/iam/docs/understanding-roles#iam.roles.admin"
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

  required_azuread_roles_to_provision_msft_365_source = {
    "7ab1d382-f21e-4acd-a863-ba3e13f7da61" = "Cloud Application Administrator",
  }
}
