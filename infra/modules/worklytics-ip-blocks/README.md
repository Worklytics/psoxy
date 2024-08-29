# worklytics-ip-blocks

*This module is NOT supported by Worklytics; YMMV. It is provided as-is, with no guarantees of
correctness.*

This module exposes the set of CIDR blocks to which you can restrict inbound traffic to your proxy
instances, for the purposes of limiting it to Worklytics.

Generally speaking, this module should NOT be used as a primary security control. The primary
ACL control for proxy access is your Cloud host's IAM and workload identity federation between
Worklytics and your cloud. See more info in the documentation site:

https://docs.worklytics.co/psoxy/faq-security#can-psoxy-invocation-be-locked-to-a-set-of-known-ip-addresses

You must ensure the IAM grants you define are correct and comprehensive, and not rely on IP-based
restrictions.

This module relies on the [Terraform http provider](https://registry.terraform.io/providers/hashicorp/http/latest/docs/data-sources/http)
to obtain the published list of GCP CIDR blocks. Please review the caveats and limitations of that
approach; namely that it *only* validates the certificate chain of trust. While we view this as
sufficient for this use-case, since this is NOT a primary security control - you should evaluate
this risk for yourself.

See:
https://cloud.google.com/compute/docs/faq#find_ip_range


## Usage
```hcl

module "worklytics-ip-blocks" {
  source = "git::https://github.com/worklytics/psoxy//infra/modules/worklytics-ip-blocks?ref=v0.4.59"

  tenant_location = "us"
}

resource "aws_security_group_rule" "https_in_from_worklytics" {
  description       = "allow HTTPS in from Worklytics"
  from_port         = 0
  protocol          = "tcp"
  security_group_id = aws_security_group.default.id
  to_port           = 443
  type              = "ingress"
  cidr_blocks       = module.worklytics-ip-blocks.cidr_blocks
}
```

## Alternatives

Use the [`google_netblock_ip_ranges`] Terraform data resource, as follows:


```hcl
data "google_netblock_ip_ranges" "worklytics-ip-blocks" {
}


resource "aws_security_group_rule" "https_in_from_worklytics" {
    description       = "allow HTTPS in from Worklytics"
    from_port         = 0
    protocol          = "tcp"
    security_group_id = aws_security_group.default.id
    to_port           = 443
    type              = "ingress"
    cidr_blocks       = data.worklytics-ip-blocks.cidr_blocks
}
```

NOTE: this will be a slightly broader list, as it includes all GCP CIDR blocks, not just those in
a given multi-region.


## Development

To test this, run in the module's directory (this directory, `infra/modules/worklytics-ip-blocks`):

```bash
terraform init

terraform apply

# or for EU case

terraform apply --var tenant_location=eu
```


