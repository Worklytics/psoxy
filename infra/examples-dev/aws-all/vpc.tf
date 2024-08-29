##
### locals that function like variables, but without tedium of declaring them in a separate file
#locals {
#  # must pick one that supports arm64 lambdas (which apparently is not all of them)
#  # and probably should be from the same region as the rest of your infra (eg, var.aws_region)
#  # don't really care about high-availability or anything; Worklytics will handle short outages and
#  # retry as needed
#  availability_zone = "us-east-1a"
#}

# these will pick up default VPC/subnet/security group for your AWS Account
# you can override these with your own VPC/subnet/security group if you want; if you do, you must
# ensure they are configured to provide connectivity to the AWS services on which your proxy setup
# depends (at minimum: S3, SSM, CloudWatch)

# alternatively, can use aws_vpc resource to create a new VPC; in such case, will likely need
# to create a gateway for it, route table, etc - to provide connectivity to AWS services and
# the data source APIS to which you intend to connect (usually on the public internet)
#resource "aws_default_vpc" "default" {
#}
#
#
#
## alternatively, can use 'aws_subnet' resource to create a new subnet
#resource "aws_default_subnet" "default" {
#  availability_zone = local.availability_zone
#}
#
## if you have a default security group, could use this as below:
##resource "aws_default_security_group" "default" {
##}
#
#resource "aws_vpc_endpoint" "aws_services" {
#  for_each = toset([
#    #"s3", # doesn't play nice with this for some reason; always 'Error: updating EC2 VPC Endpoint (vpce---): InvalidParameter: To set PrivateDnsOnlyForInboundResolverEndpoint to true, the VPC vpc--- must have a Gateway endpoint for the service.'
#    "secretsmanager",
#    "ssm"
#  ])
#
#  vpc_id       = aws_default_vpc.default.id
#  service_name = "com.amazonaws.${var.aws_region}.${each.key}"
#  vpc_endpoint_type = "Interface" # via AWS console, seems to be this
#  security_group_ids = [
#    aws_security_group.default.id
#  ]
#
#  # DNS seems to resolve it ... but is it correct?
#  private_dns_enabled = true
#
#  # unless you specify a subnet, it doesn't seem to get an IP address
#  subnet_ids = [
#    aws_default_subnet.default.id
#  ]
#}
#
#resource "aws_security_group" "default" {
#  vpc_id = aws_default_vpc.default.id
#}
#
#resource "aws_security_group_rule" "aws_services_https" {
#  description = "allow HTTPS out to AWS services"
#  from_port         = 0
#  protocol          = "tcp"
#  security_group_id = aws_security_group.default.id
#  to_port           = 0
#  type              = "egress"
#  cidr_blocks       = [ "0.0.0.0/0" ]
#  #cidr_blocks = concat(aws_vpc_endpoint.aws_services[*].cidr_blocks)
#}
