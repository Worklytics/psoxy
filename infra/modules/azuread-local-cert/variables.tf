variable "rotation_days" {
  type        = number
  default     = 60
  description = "terraform should rotate cert after this many days"
}

variable "cert_expiration_days" {
  type        = number
  default     = 180
  description = "cert will expire in this many days"
}

variable "application_id" {
  type        = string
  description = "application ID of the Azure AD application to authenticate; this is NOT the object ID of the application"
}

variable "certificate_subject" {
  type        = string
  description = "value for 'subject' passed to openssl when generation certificate (eg '/C=US/ST=New York/L=New York/CN=www.worklytics.co')"
}
