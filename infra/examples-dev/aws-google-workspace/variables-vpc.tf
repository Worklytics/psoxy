variable "vpc_ip_block" {
  type        = string
  description = "IP block for VPC to create for psoxy instances, in CIDR notation"
  default     = "10.0.0.0/18"
}
