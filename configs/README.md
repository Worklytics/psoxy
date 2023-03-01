**DEPRECATED**

The configs in this directory are left only for backwards compatibility, to support people whose
Terraform configurations are based on v0.4 modules prior to v0.4.13, when many environment variables
were loaded from these YAML files. From v0.4.13 onwards, these environment variables will be filled
from values in the `worklytics-connector-specs` module.

If your Terraform config uses a `modular-example` with version > 0.4.13, this directory is
irrelevant to you.

If your Terraform config directly uses `modules`, such as `aws-psoxy-rest`/`gcp-psoxy-rest`, you
should review the variables.tf in those modules and ensure you're filling everything that would
normally be in the YAML files in this directory. If so, you can pass `null` for the config
path and the corresponding YAML file in this directory will be ignored.

