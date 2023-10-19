variable "tf_runner_email" {
  type        = string
  description = "Email address of the Terraform Cloud runner (SA/user terraform is running as, if already known.  If omitted, will attempt to detect."
  default     = null
}
