# Lambdas on a VPC

**beta** - This is now available for customer-use, but may still change in backwards incompatible
ways.

Our `aws-host` module provides a `vpc_config` variable to specify the VPC configuration for the
lambdas that our Terraform modules will create, analogous to the
[`vpc_config`](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/lambda_function#vpc_config)
block supported by the AWS lambda terraform resource.

Some caveats:

- API connectors on a VPC must be exposed via [API Gateway](https://aws.amazon.com/api-gateway/)
  rather than [Function URLs](https://docs.aws.amazon.com/lambda/latest/dg/lambda-urls.html) (our Terraform modules will make this change for you).
- VPC *must* be configured such that your lambda has connectivity to AWS services including S3, SSM,
  and CloudWatch Logs; this is typically done by adding a [VPC Endpoint](https://docs.aws.amazon.com/vpc/latest/userguide/vpce-gateway.html)
  for each service.
- VPC *must* allow any API connector to connect to data source APIs via HTTPS (eg 443); usually
  these APIs are on the public internet, so this means egress to public internet.
- VPC *must* allow your API gateway to connect to your lambdas.

The requirements above MAY require you to modify your VPC configuration, and/or the security groups
to support proxy deployment.  The example we provide in our [`vpc.tf`](https://github.com/Worklytics/psoxy-example-aws/blob/main/vpc.tf)
should fulfill this if you adapt it; or you can use it as a reference to adapt you existing VPC.

To put the lambdas created by our terraform example under a VPC, please follow one of the approaches
documented in the next sections.

## Usage - Bring-your-own VPC
If you have an existing VPC, you can use it with the `vpc_config` variable by hard coding the ids
of the pre-existing resources (provisioned outside the scope of your proxy's terraform configuration).

```hcl
module "psoxy" {
  # lines above omitted ...

  vpc_config = {
    vpc_id             = "vpc-0a1b2c3d4e5f67890"
    security_group_ids = ["sg-0a1b2c3d4e5f67890"]
    subnet_ids         = ["subnet-0a1b2c3d4e5f67890"]
  }
}
```

## Usage - with `vpc.tf`

If you don't have a pre-existing VPC, you wish to use, our [aws example repo](https://github.com/Worklytics/psoxy-example-aws)
includes [`vpc.tf`](https://github.com/Worklytics/psoxy-example-aws/blob/main/vpc.tf) file at the
top-level. This file has a bunch of commented-out terraform resource blocks that can serve as
examples for creating the minimal VPC + associated infra.  Review and uncomment to meet your use-case.

Prerequisites:

- the AWS principal (user or role) you're using to run Terraform must have permissions to manage
  VPCs, subnets, and security groups. The AWS managed policy `AmazonVPCFullAccess` provides this.
- all pre-requisites for the api-gateways (see [api-gateway.md](./api-gateway.md))

NOTE: if you provide `vpc_config`, the value you pass for `use_api_gateway_v2` will be ignored;
using a VPC **requires** API Gateway v2, so will override value of this flag to `true`.

Add the following to "psoxy" module in your `main.tf` (or uncomment if already present):

```hcl
module "psoxy" {
  # lines above omitted ...

  vpc_config = {
      vpc_id             = aws_default_vpc.default.id
      security_group_ids = [aws_default_security_group.default.id]
      subnet_ids         = [aws_default_subnet.default.id]
  }
}
```

Uncomment the relevant lines in `vpc.tf` in the same directory, and modify as you wish. This file
pulls the default VPC/subnet/security group for your AWS account under terraform.

Alternatively, you modify `vpc.tf` to use a provision non-default VPC/subnet/security group, and
reference those from your `main.tf` - subject to the caveats above.

See the following terraform resources that you'll likely need:
- [aws_vpc](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/vpc)
- [aws_subnet](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/subnet)
- [aws_security_group](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/security_group)
- [aws_vpc_endpoint](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/vpc_endpoint)
- [aws_route_table](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/route_table)
- [aws_internet_gateway](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/internet_gateway)


## Troubleshooting

Check your Cloud Watch logs for the lambda. Proxy lambda will time out in INIT phase if SSM
Parameter Store *or* your secret store implementation (AWS Secrets Manager, Vault) is not reachable.

Some potential causes of this:
  - DNS failure - it's going to look up the SSM service by domain; if the DNS zone for the SSM
    endpoint you've provisioned is not published on the VPC, this will fail; similarly, if the
    endpoint wasn't configured on a subnet - then it won't have an IP to be resolved.
  - if the IP is resolved, you should see failure to connect to it in the logs (timeouts); check
    that your security groups for lambda/subnet/endpoint allow bidirectional traffic necessary for
    your lambda to retrieve data from SSM via the REST API.

## Switching back from using a VPC

Terraform with aws provider doesn't seem to play nice with lambdas/subnets; the subnet can't be
destroyed w/o destroying the lambda, but terraform seems unaware of this and will just wait forever.

So:

1. destroy all your lambdas (`terraform state list | grep aws_lambda_function`; then
   `terraform destroy --target=` for each, remember '' as needed)
2. destroy the subnet `terraform destroy --target=aws_subnet.main`

## References
 - https://docs.aws.amazon.com/lambda/latest/dg/foundation-networking.html
 - https://docs.aws.amazon.com/lambda/latest/dg/configuration-vpc.html
