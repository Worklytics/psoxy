variable "org_id" {
  type = string
}
variable "billing_account" {
  type = string
}

variable "group_org_admins" {
  type        = string
  description = "Google group of org admins"
}

variable "group_billing_admins" {
  type        = string
  description = "Google group of billing admins"
}

variable "project_id" {
  type        = string
  description = "id of GCP project to hold Terraform state"
}

variable "default_region" {
  type    = string
  default = "us-central1"
}
