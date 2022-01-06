variable "caller_aws_account_id" {
  type        = string
  description = "id of worklytics sa"
  default     =  "914358739851:root"
}

#eg "780C7DE5BBF9127"
variable "caller_aws_user_id" {
  type        = string
  description = "id of service account that will call proxy (eg, SA of your worklytics instance)"
}
