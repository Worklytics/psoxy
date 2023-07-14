# Bulk File Sanitization

## Overview

Psoxy can be used to sanitize bulk files (eg, CSV, etc), writing the result to another bucket.

You can automate a data pipeline to push files to an `-input` bucket, which will trigger a Psoxy
instance (GCP Cloud Function or AWS Lambda), which will read the file, sanitize it, and write the
result to a corresponding `-sanitized` bucket.


## Sanitization Rules

The 'bulk' mode of Psoxy supports column-oriented sanitization rules.

### Pseudonymization
The core function of the Proxy is to pseudonymize PII in your data. To pseudonymize a column, add it
to `columnsToPseudonymize`.

```yaml
columnsToPseudonymize:
  - employee_id
  - employee_email
  - manager_id
```

To avoid inadvertent data leakage, if a column specified to be pseudonymized is not present in the
input data, the Proxy will fail with an error. This is to avoid simple column name typos resulting
in data leakage.

### Additional Transformations

To ease integration, the 'bulk' mode also supports a few additional common transformations that may
be useful. These provide an alternative to using a separate ETL tool to transform your data, or
modifying your existing data export pipelines.

#### Redaction

To redact a column, add it to `columnsToRedact`.  By default, all columns present in the input data
will be included in the output data, unless explicitly redacted.

```yaml
columnsToRedact:
  - salary
```

#### Inclusion
Alternatively to redacting columns, you can specify `columnsToInclude`. If specified, only columns
explicitly included will be included in the output data.

```yaml
columnsToInclude:
  - employee_id
  - employee_email
  - manager_id
  - team
  - department
```

#### Renaming Columns

To rename a column, add it to `columnsToRename`, which is a map from original name --> desired name.
Renames are applied before pseudonymization.

```yaml
columnsToRename:
  termination_date: leave_date
```

This feature supports simple adaptation of existing data pipelines for use in Worklytics.

### Configuration

Worklytics' provided Terraform modules include default rules for expected formats for `hris`,
`survey`, and `badge` data connectors.

If your input data does not match the expected formats, you can customize the rules in one of the
following ways.

*NOTE: The configuration approaches described below utilized Terraform variables as provided by our
gcp and aws template examples.  Other examples may not support these variables; please consult the
`variables.tf` at the root of your configuration.  If you are directly using Worklytics' Terraform
modules, you can consult the `variables.tf` in the module directory to see if these variables are
exposed.*


### Custom Rules of Predefined Bulk Connector (preferred)
You can override the rules used by the predefined bulk connectors (eg `hris`, `survey`, `badge`) by
filling the `custom_bulk_connector_rules` variable in your Terraform configuration.

This variable is a map from connector ID --> rules, with the rules encoded in HCL format (rather
than YAML as shown above).  An illustrative example:

```hcl
custom_bulk_connector_rules = {
  hris = {
    pseudonymFormat = "URL_SAFE_TOKEN"
    columnsToRename = {
      termination_date = "leave_date"
    }
    columnsToPseudonymize = [
      "employee_id",
      "employee_email",
      "manager_id"
    ]
    columnsToRedact = [
      "salary"
    ]
  }
}
```

### Custom Bulk Connector

Rather than enabling one of the predefined bulk connectors providing in the `worklytics-connector-specs`
Terraform module, you can specify a custom connector from scratch, including your own rules.

This approach is less convenient than the previous one, as TODO documentation and deep-links for
connecting your data to Worklytics will not be generated.

To create a Custom Bulk Connector, use the `custom_bulk_connectors` variable in your Terraform
configuration, for example:

```hcl
custom_bulk_connectors = {
  my_custom_bulk_connector_id = {
    sourceKindId = "my_custom_bulk_data"
    rules = {
      pseudonymFormat = "URL_SAFE_TOKEN"
      columnsToRename = {
        termination_date = "leave_date"
      }
      columnsToPseudonymize = [
        "employee_id",
        "employee_email",
        "manager_id"
      ]
      columnsToRedact = [
        "salary"
      ]
    }
  }
}
```

### Direct Configuration of RULES

You can directly modify the `RULES` environment variable on the Psoxy instance, by directly editing
your instance's environment via your hosting provider's console or CLI.  In this case, the rules
should be encoded in YAML format, such as:

```yaml
pseudonymFormat: URL_SAFE_TOKEN
columnsToRename:
    termination_date: leave_date
columnsToPseudonymize:
  - employee_id
  - employee_email
  - manager_id
columnsToRedact:
  - salary
```

Alternatively, you can remove the environment variable from your instance, and instead configure a
`RULES` value in the "namespace" of your instance, in the AWS Parameter Store or GCP Secret Manager
(as appropriate for your hosting provider).

This approach is useful for testing, but note that if you later run `terraform apply` again, any
changes you make to the environment variable may be overwritten by Terraform.



## See Also

  - Rule structure is specified in [`ColumnarRules`](java/gateway-core/src/main/java/com/avaulta/gateway/rules/ColumnarRules.java).

