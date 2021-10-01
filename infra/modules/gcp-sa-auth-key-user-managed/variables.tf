
variable "service_account_id" {
  type        = string
  description = "ID of the service account"
}

variable "key_admin_group_email" {
  type        = string
  description = "email of Google group that should be able to create keys for SA"
}
