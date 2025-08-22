# Upgrade Proxy Versions

Prior to any upgrade, please determine your current version and target version, then review the [`CHANGELOG.md`](https://github.com/Worklytics/psoxy/blob/main/CHANGELOG.md) and [release notes](https://github.com/Worklytics/psoxy/releases) your target version, and all intermediary versions, for any significant changes that may require additional action on your part.

Generally speaking, prior to a `v1`, we strive to following semantic versioning as follows: `v{x}.{y}.{z}`

## Deployments Using Our Examples (from `v0.4.30` and later)

To ease upgrading versions, our example repos ([psoxy-example-aws](https://github.com/Worklytics/psoxy-example-aws) or [psoxy-example-gcp](https://github.com/Worklytics/psoxy-example-gcp) since `v0.4.30` include a script to update all the version references in your configuration.

```shell
./upgrade-terraform-modules v0.5.7
```

This will update all the versions references throughout your example, and offer you a command to revert if you later wish to do so.  A `terraform init` with the appropriate `-upgrade` flag will be run automatically.

After this, you must still run `terraform apply` to apply the changes to your infrastructure. (we recommend `terraform plan` first to preview the changes).

## Legacy Deployments (Initial version pre-`v0.4.30`)
If you initially used one of our examples prior to `v0.4.30`, or did not use one of our examples, you will need to manually update the version references in your configuration.


Open each `.tf` file in the root of your configuration. Find all module references ending in a version number, and update them to the new version.

Eg, look for something like the following:

```hcl
module "psoxy" {
  source = "github.com/Worklytics/psoxy//terraform?ref=v0.4.37"
}
```

update the `v0.4.37` to `v0.4.62`:

```hcl
module "psoxy" {
  source = "github.com/Worklytics/psoxy//terraform?ref=v0.4.62"
}
```

Then run `terraform init` after saving the file to download the new version of each module(s).
