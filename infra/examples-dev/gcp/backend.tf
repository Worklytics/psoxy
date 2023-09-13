terraform {
  # we recommend you use a secure location for your Terraform state (such as cloud storage bucket),
  # as it may contain sensitive values (such as API keys) depending on which data sources you
  # configure.
  #
  # local may be safe for production-use if and ONLY if you are executing Terraform from a secure
  # location.
  #
  # Please review and seek guidance from your Security team if in doubt.
  backend "local" {
  }

  # example backend (this GCS bucket must already be provisioned, and gcloud CLI where terraform is
  # applied from must be to read/write to it)
  # see https://www.terraform.io/docs/backends/types/gcs.html for more details
  #  backend "gcs" {
  #    bucket  = "tf-state-prod"
  #    prefix  = "proxy/terraform-state"
  #  }

}
