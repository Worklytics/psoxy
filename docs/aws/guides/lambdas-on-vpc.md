# Lambdas on a VPC

**alpha** - this is a work in progress, and unsupported; may change in backwards incompatible ways.

Our `aws-host` module provides a `vpc_config` variable to specify the VPC configuration for the
lambdas that our Terraform modules will create, analogous to the
[`vpc_config`](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/lambda_function#vpc_config)
block supported by the AWS lambda terraform resource.

Some caveats:

- API connectors on a VPC must be exposed via [API Gateway](https://aws.amazon.com/api-gateway/)
  rather than [Function URLs](https://docs.aws.amazon.com/lambda/latest/dg/lambda-urls.html)
- as of v0.4.46, we've seen requests to lambdas on VPCs timing out for some time after initial
  deployment; we've seen this apparently resolve on its own after a few minutes, but it's not what's
  happening.

## Usage - with `vpc.tf`

Prequisites:

- the AWS principal (user or role) you're using to run Terraform must have permissions to manage
  VPCs, subnets, and security groups. The AWS managed policy `AmazonVPCFullAccess` provides this.
- all pre-requisites for the api-gateways (see [api-gateway.md](./api-gateway.md))

NOTE: if you provide `vpc_config`, the value you pass for `use_api_gateway_v2` will be ignored;
using a VPC **requires** API Gateway v2.

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

## Switching back from using a VPC

Terraform with aws provider doesn't seem to play nice with lambdas/subnets; the subnet can't be
destroyed w/o destroying the lambda, but terraform seems unaware of this and will just wait forever.

So:

1. destroy all your lambdas (`terraform state list | grep aws_lambda_function`; then
   `terraform destroy --target=` for each, remember '' as needed)
2. destroy the subnet `terraform destroy --target=aws_subnet.main`
