##
### locals that function like variables, but without tedium of declaring them in a separate file
#locals {
#
#  cidr_block = "10.0.0.0/16"
#
#  # must pick one that supports arm64 lambdas (which apparently is not all of them)
#  availability_zone = "us-east-1a"
#}
#
#
### actual logic
#resource "aws_vpc" "main" {
#  cidr_block = local.cidr_block
#
#  tags = {
#    environment_name = var.environment_name
#  }
#}
#
#resource "aws_subnet" "main" {
#  vpc_id            = aws_vpc.main.id
#  cidr_block        = local.cidr_block
#  availability_zone = local.availability_zone
#
#  tags = {
#    environment_name = var.environment_name
#  }
#}
#
#
#resource "aws_security_group" "main" {
#  name        = "${var.environment_name}"
#  description = "Security group for ${var.environment_name} deployment of Psoxy"
#  vpc_id      = aws_vpc.main.id
#
#  # allow HTTPS inbound from any source
#  ingress {
#    description = "HTTPS"
#    from_port   = 443
#    to_port     = 443
#    protocol    = "tcp"
#    cidr_blocks = [
#      local.cidr_block
#    ]
#  }
#
#  # Allow HTTPS outbound to any destination
#  egress {
#    from_port = 443
#    to_port   = 443
#    protocol  = "tcp"
#    cidr_blocks = [
#      "0.0.0.0/0" # any destination
#    ]
#  }
#}
