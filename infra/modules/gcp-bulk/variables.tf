variable "project_id" {
  type        = string
  description = "id of GCP project that will host psoxy instance"
}

variable "worklytics_sa_emails" {
  type        = list(string)
  description = "service accounts for your organization's Worklytics instances (list supported for test/dev scenarios)"
}

variable "region" {
  type        = string
  description = "region into which to deploy function"
  default     = "us-central1"
}

variable "bucket_prefix" {
  type        = string
  description = "Prefix for buckets. Buckets will be created adding a suffix -import and -processed to this prefix"
}

variable "bucket_location" {
  type        = string
  description = "Location where buckets will be deployed"
  default     = "US"
}

variable "source_kind" {
  type        = string
  description = "Kind of the content to process"
}
