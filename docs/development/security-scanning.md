

## Locally


### Trivy

```shell
brew install trivy
```

In each dev example
```shell
trivy config --tf-vars terraform.tfvars .
```

### tfsec

Older tooling; migrated to trivy above, but well-known:

```shell
brew install tfsec
```

In each dev example
```shell
tfsec --tfvars-file=terraform.tfvars
```
