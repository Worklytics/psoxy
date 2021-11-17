variable "project_name" {
  type        = string
  description = "name to give to GCP project"
  default     = null
}

variable "project_id" {
  type        = string
  description = "id of GCP project to hold Terraform state"
}

variable "kms_resource_location" {
  type        = string
  description = "location in which Cloud KMS key rings (and keys) should reside (see https://cloud.google.com/kms/docs/locations)"
  default     = "us" # recommended alternatives: global, europe
}

variable "storage_location" {
  type        = string
  description = "location in which Cloud Storage buckets should reside (see https://cloud.google.com/storage/docs/locations)"
  default     = "US" # recommended alternatives : EU, ASIA
}


variable "project_labels" {
  type        = map(string)
  description = "labels to assign to project"
  default     = {}
}

variable "bucket_labels" {
  type        = map(string)
  description = "labels to assign to bucket"
  default     = {}
}
