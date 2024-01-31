terraform {

  # we recommend you use a secure location for your Terraform state (such as S3 bucket), as it
  # may contain sensitive values (such as API keys) depending on which data sources you configure.
  #
  # local may be safe for production-use IFF you are executing Terraform from a secure location
  #
  # Please review and seek guidance from your Security team if in doubt.
  backend "local" {
  }

  # example backend (this S3 bucket must already be provisioned, and, when you run 'terraform apply'
  # Terraform must be authenticated as an AWS principal allowed to read/write to it - and use its
  # encryption key, if any)
  #  backend "s3" {
  #    bucket = "terraform_state_bucket" # fill with S3 bucket where you want the statefile to be
  #    key    = "prod_state" # fill with path where you want state file to be stored
  #    region = "us-east-1" # cannot be a variable
  #  }
}
