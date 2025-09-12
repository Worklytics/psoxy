# Releases

## Prepare Release Candidate

From `main`:

```shell
./tools/release/prep.sh v0.4.15 rc-v0.4.16
```

- follow steps output by that tool
- if need interim testing, create a "branch" of the release (eg, branch `v0.4.16` instead of tag),
  and trigger `gh workflow run ci-terraform-examples-release.yaml`

## Release

On `rc-`:

```shell
./tools/release/prep.sh rc-v0.4.16 v0.4.16
```

QA aws, gcp dev examples by running `terraform apply` for each, and testing various connectors.


Create PR to merge `rc-` to `main`.

```shell
./tools/release/rc-to-main.sh v0.4.16
```

After merged to `main`:

```shell
./tools/release/publish.sh v0.4.16
```
