# VPC for Lambda + API Gateway. Uses the account default VPC, a public subnet (internet
# gateway + NAT) and a private subnet (Lambda).
#
# NAT and the Lambda must not share a subnet. The public route table sends 0.0.0.0/0 to the
# internet gateway; the private route table sends 0.0.0.0/0 to the NAT. One route table cannot
# do both, and a Lambda ENI has no public IP, so a public subnet alone cannot reach Google,
# Microsoft, and other data-source APIs.
#
# Attaching an existing Lambda to aws_subnet.private replaces its ENIs. If apply hangs
# destroying the old subnet, destroy the Lambda functions first, then the subnet.
# See docs/aws/guides/lambdas-on-vpc.md.

locals {
  availability_zone = "${var.aws_region}a"
}

#trivy:ignore:AVD-AWS-0101 # example adopts the account default VPC; a dedicated VPC is optional
resource "aws_default_vpc" "default" {}

# Public subnet: NAT must live here, with a route to the internet gateway.
resource "aws_default_subnet" "public" {
  availability_zone = local.availability_zone
}

# Private subnet for Lambda ENIs. Outbound internet goes via NAT, not the internet gateway.
# Index 15 is the top /20 of a default /16, above the /20s AWS creates for the default subnets.
resource "aws_subnet" "private" {
  vpc_id                  = aws_default_vpc.default.id
  availability_zone       = local.availability_zone
  cidr_block              = cidrsubnet(aws_default_vpc.default.cidr_block, 4, 15)
  map_public_ip_on_launch = false
}

data "aws_internet_gateway" "default" {
  filter {
    name   = "attachment.vpc-id"
    values = [aws_default_vpc.default.id]
  }
}

resource "aws_security_group" "default" {
  vpc_id      = aws_default_vpc.default.id
  name        = "${var.environment_name}-lambda"
  description = "Psoxy Lambda ENIs and interface VPC endpoints"
}

# Interface VPC endpoints need inbound 443 from Lambda ENIs (same security group here).
resource "aws_security_group_rule" "ingress_https_self" {
  description              = "allow HTTPS between Lambda ENIs and interface VPC endpoints"
  type                     = "ingress"
  from_port                = 443
  to_port                  = 443
  protocol                 = "tcp"
  security_group_id        = aws_security_group.default.id
  source_security_group_id = aws_security_group.default.id
}

#trivy:ignore:AVD-AWS-0104 # data-source APIs are arbitrary public hosts; egress is TCP 443 only
resource "aws_security_group_rule" "egress_https" {
  description       = "allow HTTPS egress to external APIs"
  type              = "egress"
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  security_group_id = aws_security_group.default.id
  cidr_blocks       = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "egress_dns_udp" {
  description       = "allow DNS to VPC resolver for external hostnames"
  type              = "egress"
  from_port         = 53
  to_port           = 53
  protocol          = "udp"
  security_group_id = aws_security_group.default.id
  cidr_blocks       = [aws_default_vpc.default.cidr_block]
}

resource "aws_security_group_rule" "egress_dns_tcp" {
  description       = "allow DNS over TCP to VPC resolver"
  type              = "egress"
  from_port         = 53
  to_port           = 53
  protocol          = "tcp"
  security_group_id = aws_security_group.default.id
  cidr_blocks       = [aws_default_vpc.default.cidr_block]
}

resource "aws_eip" "nat" {
  domain = "vpc"
}

resource "aws_route_table" "public" {
  vpc_id = aws_default_vpc.default.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = data.aws_internet_gateway.default.id
  }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_default_subnet.public.id
  route_table_id = aws_route_table.public.id
}

resource "aws_nat_gateway" "default" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_default_subnet.public.id

  depends_on = [aws_route_table_association.public]
}

resource "aws_route_table" "private" {
  vpc_id = aws_default_vpc.default.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.default.id
  }
}

resource "aws_route_table_association" "private" {
  subnet_id      = aws_subnet.private.id
  route_table_id = aws_route_table.private.id
}

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_default_vpc.default.id
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.public.id, aws_route_table.private.id]
}

resource "aws_vpc_endpoint" "aws_services" {
  for_each = toset([
    "secretsmanager",
    "ssm",
    "kms",
    "logs",
  ])

  vpc_id              = aws_default_vpc.default.id
  service_name        = "com.amazonaws.${var.aws_region}.${each.key}"
  vpc_endpoint_type   = "Interface"
  security_group_ids  = [aws_security_group.default.id]
  private_dns_enabled = true
  subnet_ids          = [aws_subnet.private.id]
}
