variable "connector_id" {
  type        = string
  description = "name of the gcp project"
}

variable "refresh_token_endpoint" {
  type        = string
  description = "email of the service account that the cloud function will run as"
}