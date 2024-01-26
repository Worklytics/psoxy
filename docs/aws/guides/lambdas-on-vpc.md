# Lambdas on a VPC

**alpha** - this is a work in progress, and unsupported; may change in backwards incompatible ways.


Our `aws-host` module provides a `vpc_config` variable to specify the VPC configuration for the
lambdas that our Terraform modules will create, analogous to the [`vpc_config`](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/lambda_function#vpc_config)
block supported by the AWS lambda terraform resource.

Some caveats:
  - API connectors on a VPC must be exposed via [API Gateway](https://aws.amazon.com/api-gateway/)
    rather than [Function URLs](https://docs.aws.amazon.com/lambda/latest/dg/lambda-urls.html)



## Usage - with `vpc.tf`

Prequisites:
 - the AWS principal (user or role) you're using to run Terraform must have permissions to manage
   VPCs, subnets, and security groups. The AWS managed policy `AmazonVPCFullAccess` provides this.
 - all pre-requisites for the api-gateways (see [api-gateway.md](./api-gateway.md))

Add the following to "psoxy" module in your `main.tf` (or uncomment if already present):

```hcl
module "psoxy" {
  # lines above omitted ...

  vpc_config = {
    vpc_id             = aws_vpc.main.id
    security_group_ids = [ aws_security_group.main.id ]
    subnet_ids         = [ aws_subnet.main.id ]
  }
}
```

Uncomment the relevant lines in `vpc.tf` in the same directory, and modify as you wish. This file
provisions a VPC, subnet, and security group for use by the lambdas.


