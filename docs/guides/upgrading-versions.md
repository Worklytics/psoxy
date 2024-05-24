# Upgrade Proxy Versions

There are two approaches to upgrade you Proxy to a newer version.

In both cases, you should carefully review your next `terraform plan` or `terraform apply` for
changes to ensure you understand what will be created, modified, or destroyed by the upgrade.

If you have doubts, review
[`CHANGELOG.md`](https://github.com/Worklytics/psoxy/blob/main/CHANGELOG.md) for highlights of
significant changes in each version; and detailed release notes for each release:

[https://github.com/Worklytics/psoxy/releases](https://github.com/Worklytics/psoxy/releases)

## Using `upgrade-terraform-modules` Script

If you originally used one of our example repos
([psoxy-example-aws](https://github.com/Worklytics/psoxy-example-aws) or
[psoxy-example-gcp](https://github.com/Worklytics/psoxy-example-gcp), etc), starting from version
`v0.4.30`, you can use the following command leveraging a script creating when you initialized the
example:

```shell
./upgrade-terraform-modules v0.4.46
```

This will update all the versions references throughout your example, and offer you a command to
revert if you later wish to do so.

## Find and replace version references

Open each `.tf` file in the root of your configuration. Find all module references ending in a
version number, and update them to the new version.

Eg, look for something like the following:

```hcl
module "psoxy" {
  source = "github.com/Worklytics/psoxy//terraform?ref=v0.4.37"
}
```

update the `v0.4.37` to `v0.4.46`:

```hcl
module "psoxy" {
  source = "github.com/Worklytics/psoxy//terraform?ref=v0.4.46"
}
```

Then run `terraform init` after saving the file to download the new version of each module(s).
