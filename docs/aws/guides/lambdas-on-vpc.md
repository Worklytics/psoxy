# Lambdas on a VPC

**beta** - This is now available for customer-use, but may still change in backwards incompatible ways.

Our `aws-host` module provides a `vpc_config` variable to specify the VPC configuration for the lambdas that our Terraform modules will create, analogous to the [`vpc_config`](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/lambda_function#vpc_config) block supported by the AWS lambda terraform resource.

Some caveats:

- API connectors on a VPC must be exposed via [API Gateway](https://aws.amazon.com/api-gateway/) rather than [Function URLs](https://docs.aws.amazon.com/lambda/latest/dg/lambda-urls.html) (our Terraform modules will make this change for you). API Gateway invokes the function through the Lambda service, which does not cross the VPC, so invocation does not need an `execute-api` endpoint.
- The VPC must reach the AWS APIs the function calls. Use the endpoints in [AWS APIs the proxy calls](#aws-apis-the-proxy-calls). A Lambda ENI has no public IP, so placing it in a public subnet with an internet gateway does not provide that access.
- Data-source APIs are usually on the public internet (HTTPS, port 443). Reach them with a NAT gateway in a public subnet and a private subnet for the Lambda whose default route points at that NAT. See [Enable internet access for VPC-connected Lambda functions](https://docs.aws.amazon.com/lambda/latest/dg/configuration-vpc-internet.html).

The requirements above MAY require you to modify your VPC configuration, and/or the security groups to support proxy deployment. The example we provide in our [`vpc.tf`](https://github.com/Worklytics/psoxy-example-aws/blob/main/vpc.tf) should fulfill this if you adapt it; or you can use it as a reference to adapt your existing VPC.

To put the lambdas created by our terraform example under a VPC, please follow one of the approaches documented in the next sections.

## Usage - Bring-your-own VPC

If you have an existing VPC, you can use it with the `vpc_config` variable by hard coding the ids of the pre-existing resources (provisioned outside the scope of your proxy's terraform configuration).

```hcl
module "psoxy" {
  # lines above omitted ...

  vpc_config = {
    security_group_ids = ["sg-0a1b2c3d4e5f67890"]
    subnet_ids         = ["subnet-0a1b2c3d4e5f67890"]
  }
}
```

`subnet_ids` must be private subnets whose default route is a NAT gateway. A public subnet with only an internet gateway does not give the Lambda a public IP, so calls to data-source APIs time out.

## Usage - with `vpc.tf`

If you don't have a pre-existing VPC you wish to use, our [aws example repo](https://github.com/Worklytics/psoxy-example-aws) includes [`vpc.tf`](https://github.com/Worklytics/psoxy-example-aws/blob/main/vpc.tf) at the top level. It adopts the default VPC, puts a NAT gateway in the default public subnet (`aws_default_subnet.public`), and places the Lambda in a separate private subnet (`aws_subnet.private`). The public route table sends `0.0.0.0/0` to the internet gateway. The private route table sends `0.0.0.0/0` to the NAT. Those routes cannot be combined on one table: replacing the internet-gateway route with a NAT route on the subnet that hosts the NAT leaves the NAT with no path to the internet, and external HTTPS calls hang until they time out.

Prerequisites:

- the AWS principal (user or role) you're using to run Terraform must have permissions to manage VPCs, subnets, and security groups. The AWS managed policy `AmazonVPCFullAccess` provides this.
- all pre-requisites for the api-gateways (see [api-gateway.md](./api-gateway.md))

NOTE: if you provide `vpc_config`, the value you pass for `use_api_gateway_v2` will be ignored; using a VPC **requires** API Gateway v2, so will override value of this flag to `true`.

The example `main.tf` passes the private subnet into the `psoxy` module:

```hcl
module "psoxy" {
  # lines above omitted ...

  vpc_config = {
    security_group_ids = [aws_security_group.default.id]
    subnet_ids         = [aws_subnet.private.id]
  }
}
```

`vpc_config` does not take a `vpc_id`. The Lambda security group and the interface endpoints use `aws_security_group.default`: inbound TCP 443 from itself, outbound TCP 443, and outbound TCP/UDP 53 to the VPC CIDR so DNS can resolve external hostnames.

To deploy without a VPC, remove `vpc_config` from the module and remove the resources in `vpc.tf`. The NAT gateway and the interface endpoints are billed while they exist.

Alternatively, you modify `vpc.tf` to use a provision non-default VPC/subnet/security group, and reference those from your `main.tf` - subject to the caveats above.

## AWS APIs the proxy calls

Lambda in a VPC can reach AWS APIs through the NAT path or through [VPC endpoints](https://docs.aws.amazon.com/vpc/latest/privatelink/vpc-endpoints.html). Interface endpoints need `private_dns_enabled` so the AWS SDK hostnames resolve to them, and their security group must allow inbound TCP 443 from the Lambda security group. The Lambda security group must allow outbound TCP 443. Select one endpoint subnet per Availability Zone where private access is needed; endpoint ENIs remain reachable from other subnets in the VPC, although cross-AZ access adds cost and reduces resilience.

| Service | Endpoint | When the function calls it |
| --- | --- | --- |
| Systems Manager Parameter Store | Interface `ssm` | Every instance. Config, locks, and the default secret store are read during init. |
| Secrets Manager | Interface `secretsmanager` | `secrets_store_implementation = "aws_secrets_manager"`. Included in the example. |
| KMS | Interface `kms` | Parameter Store secrets are SecureString values encrypted with KMS (`alias/aws/ssm` unless you set a customer key). The function reads them with decryption during init. Webhook auth keys also call `kms:GetPublicKey`. |
| CloudWatch Logs | Interface `logs` | Included in the example so Logs API calls stay on PrivateLink. |
| Amazon S3 | Gateway `s3` on the public and private route tables | Bulk connectors, webhook batch output, lookup tables, and remote resources. This does not go through the NAT. |
| SQS | Interface `sqs` | Add this when you use webhook collectors, SQS output, or async API queues. |
| Bedrock | Interface `bedrock-runtime` | `enable_gen_metadata = true`. |
| STS | Interface `sts` | Google Workspace with `api_client_auth_method = "workload_identity_federation"` (STS `GetCallerIdentity`). The Google token exchange (`sts.googleapis.com`) uses the NAT path. |
| Cognito Identity | Interface `cognito-identity` | Microsoft 365 workload identity federation (`GetOpenIdTokenForDeveloperIdentity`). |

S3 stays a gateway endpoint. An S3 interface endpoint with private DNS enabled fails unless a gateway endpoint for S3 already exists (`InvalidParameter: To set PrivateDnsOnlyForInboundResolverEndpoint to true, the VPC must have a Gateway endpoint for the service`). Interface endpoints are created in the private subnet, with `private_dns_enabled`.

See the following terraform resources that you'll likely need:

- [aws_vpc](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/vpc)
- [aws_subnet](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/subnet)
- [aws_security_group](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/security_group)
- [aws_vpc_endpoint](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/vpc_endpoint)
- [aws_route_table](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/route_table)
- [aws_internet_gateway](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/internet_gateway)
- [aws_nat_gateway](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/nat_gateway)

## Troubleshooting

Check CloudWatch Logs for the Lambda. A timeout in the log stream means the function started and then could not reach something.

The proxy times out in the INIT phase when Parameter Store, KMS, or the configured secret store (Secrets Manager or Vault) is not reachable.

- Private DNS is off, or the VPC does not have DNS resolution and DNS hostnames enabled, so `ssm.<region>.amazonaws.com` does not resolve to the endpoint.
- The interface endpoint is not in the Lambda subnet, so it has no IP there.
- The security group does not allow inbound TCP 443 from itself. Outbound HTTPS alone does not let the Lambda connect to the interface endpoint.
- The Lambda security group does not allow outbound TCP 443, or outbound TCP/UDP 53 to the VPC CIDR (DNS for names such as `googleapis.com`).

A timeout calling a data-source host (Google Calendar, Microsoft Graph, Slack, and so on), after INIT has succeeded, is the NAT path. The NAT gateway must sit in the public subnet (`0.0.0.0/0` to the internet gateway). The Lambda must sit in the private subnet (`0.0.0.0/0` to the NAT). Pointing the public subnet's default route at the NAT removes the internet gateway route, and the NAT itself cannot reach the internet.

S3 reads and writes use the gateway endpoint on the private route table. To keep webhook collector SQS calls or `enable_gen_metadata` Bedrock calls on PrivateLink rather than the NAT path, add `sqs` or `bedrock-runtime`, respectively.

## Switching back from using a VPC

Terraform with aws provider doesn't seem to play nice with lambdas/subnets; the subnet can't be destroyed w/o destroying the lambda, but terraform seems unaware of this and will just wait forever.

So:

Changing `subnet_ids` recreates the Lambda elastic network interfaces. Terraform often waits forever because it tries to destroy the subnet while the function still references it.

1. Destroy all your Lambdas (`terraform state list | grep aws_lambda_function`; then `terraform destroy --target=` for each).
2. Destroy the private subnet (`terraform destroy --target=aws_subnet.private`) after the functions are gone.

## References

- [https://docs.aws.amazon.com/lambda/latest/dg/foundation-networking.html](https://docs.aws.amazon.com/lambda/latest/dg/foundation-networking.html)
- [https://docs.aws.amazon.com/lambda/latest/dg/configuration-vpc.html](https://docs.aws.amazon.com/lambda/latest/dg/configuration-vpc.html)
