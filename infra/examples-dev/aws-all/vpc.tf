locals {
  cidr_block = "10.0.0.0/16"
}

resource "aws_vpc" "main" {
  cidr_block = local.cidr_block

  tags = {
    environment_name = var.environment_name
  }
}

resource "aws_subnet" "main" {
  vpc_id     = aws_vpc.main.id
  cidr_block = local.cidr_block

  tags = {
    environment_name = var.environment_name
  }
}


resource "aws_security_group" "main" {
  name        = "${var.environment_name}"
  description = "Security group for ${var.environment_name} deployment of Psoxy"
  vpc_id      = aws_vpc.main.id

  # allow HTTPS inbound from any source
  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = [
      local.cidr_block
    ]
  }

  # Allow HTTPS outbound to any destination
  egress {
    from_port = 443
    to_port   = 443
    protocol  = "tcp"
    cidr_blocks = [
      "0.0.0.0/0" # any destination
    ]
  }
}
