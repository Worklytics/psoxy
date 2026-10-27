terraform {
  backend "gcs" {
    bucket  = "psoxy-dev-andres-terraform-state"
    prefix  = "example-dev/gcp"
  }
}
