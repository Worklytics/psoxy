variable "instance_id" {
  type        = string
  description = "id of proxy instance (eg, `psoxy-gcal`)"
}

variable "path_to_global_secrets" {
  type    = string
  default = "secret/PSOXY_GLOBAL/"
}

variable "path_to_instance_secrets" {
  type    = string
  default = null
}
