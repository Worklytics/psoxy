locals {
  cidr_block = "10.0.0.0/16"
}

resource "aws_vpc" "main" {
  cidr_block = local.cidr_block
}

resource "aws_subnet" "main" {
  vpc_id     = aws_vpc.main.id
  cidr_block = local.cidr_block
}

resource "aws_security_group" "main" {
  name        = "psoxy"
  description = "Security group for psoxy"
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
