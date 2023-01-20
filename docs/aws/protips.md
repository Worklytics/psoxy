# Protips

Some ideas on how to support scenarios and configuration requirements beyond what our default
examples show:


## Using an AWS KMS Key to encrypt SSM Parameters

If you want to use an existing AWS KMS key to encrypt SSM parameters created by the proxy, or create
a key for this purpose, adapt the following:

```hcl
resource "aws_kms_key" "key" {
  description             = "KMS key for Psoxy"
  enable_key_rotation     = true
  is_enabled              = true
}

# pass it's id to example
module "psoxy-aws-google-workspace" {
  source = "git::https://github.com/worklytics/psoxy//infra/modular-examples/aws-google-workspace?ref=v0.4.10"

  # ... other variables omitted for brevity

  aws_ssm_key_id                 = aws_kms_key.key.key_id
}
# NOTE: if you use this, likely will have to first apply with -target=aws_kms_key.key, before doing
# the generall `terraform apply`, as some module `for_each` values will depend on the key_id
```

Our modules will give each proxy's role perms to decrypt using that key.  The role you're using to
run terraform will need to have perms to grant such permissions.





