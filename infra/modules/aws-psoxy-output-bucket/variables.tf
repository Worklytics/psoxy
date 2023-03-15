variable "instance_id" {
  type        = string
  description = "Human readable reference name for this psoxy instance. Helpful for distinguishing resulting infrastructure"

  # enforce max length to avoid bucket names that are too long
  validation {
    condition     = length(var.instance_id) < 41
    error_message = "The instance_id must be at most 40 characters."
  }
}

variable "sanitized_accessor_role_names" {
  type        = list(string)
  description = "list of names of AWS IAM Roles which should be able to access the sanitized (output) bucket"
}

variable "iam_role_for_lambda_name" {
  type        = string
  description = "IAM Role name for the lambda function that will write to this bucket"
}
