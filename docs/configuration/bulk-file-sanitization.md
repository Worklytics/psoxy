# Bulk File Sanitization

## Overview

Psoxy can be used to sanitize bulk files (eg, CSV, NDSJON, etc), writing the result to another bucket.

You can automate a data pipeline to push files to an `-input` bucket, which will trigger a Psoxy instance (GCP Cloud Function or AWS Lambda), which will read the file, sanitize it, and write the result to a corresponding `-sanitized` bucket.

You should limit the size of files processed by proxy to 200k rows or less, to ensure processing of any single file finishes within the run time limitations of the host platform (AWS, GCP). There is some flexibility here based on the complexity of your rules and file schema, but we've found 200k to be a conservative target.

### Compression

To improve performance and reduce storage costs, you should compress (gzip) the files you write to the `-input` bucket. Psoxy will decompress gzip files before processing and then compress the result before writing to the `-sanitized` bucket. Ensure that you set `Content-Encoding: gzip` on all files in your `-input` bucket to enable this behavior. Note that if you are uploading files via the web UI in GCP/AWS, it is not possible to set this metadata in the initial upload - so you cannot use compression in such a scenario.

## Sanitization Rules

The 'bulk' mode of Psoxy supports either column-oriented or record-oriented file formats.

### Column-Oriented Formats (ColumnarRules)

To cater to column-oriented file formats (Eg .csv, .tsv), Psoxy supports a `ColumnarRules` format for encoding your sanitization rules. This rules format provides simple/concise configuration for these cases, where more complex processing of repeated values / complex field types is required.

If your use-case is record oriented (eg, `NDJSON`, etc), with nested or repeated fields, then you will likely need `RecordRules` as an alternative.

#### Pseudonymization

The core function of the Proxy is to pseudonymize PII in your data. To pseudonymize a column, add it to `columnsToPseudonymize`.

```yaml
columnsToPseudonymize:
  - employee_id
  - employee_email
  - manager_id
```

To avoid inadvertent data leakage, if a column specified to be pseudonymized is not present in the input data, the Proxy will fail with an error. This is to avoid simple column name typos resulting in data leakage.


If that is not the behavior to desire, you can use `columnsToPseudonymizeIfPresent` to pseudonymize columns without this check:

```yaml
columnsToPseudonymizeIfPresent:
  - optional_employee_id
  - backup_email
  - secondary_manager_id
```

#### Additional Transformations

To ease integration, the 'bulk' mode also supports a few additional common transformations that may be useful. These provide an alternative to using a separate ETL tool to transform your data, or modifying your existing data export pipelines.

##### Redaction

To redact a column, add it to `columnsToRedact`. By default, all columns present in the input data will be included in the output data, unless explicitly redacted.

```yaml
columnsToRedact:
  - salary
```

##### Inclusion

Alternatively to redacting columns, you can specify `columnsToInclude`. If specified, only columns explicitly included will be included in the output data.

```yaml
columnsToInclude:
  - employee_id
  - employee_email
  - manager_id
  - team
  - department
```

##### Renaming Columns

To rename a column, add it to `columnsToRename`, which is a map from original name --> desired name. Renames are applied before pseudonymization.

```yaml
columnsToRename:
  termination_date: leave_date
```

This feature supports simple adaptation of existing data pipelines for use in Worklytics.

##### Duplicating Columns

To duplicate a column (creating a copy with a different name), add it to `columnsToDuplicate`, which is a map from original column name --> new column name. Column duplication is applied before pseudonymization and other transforms.

```yaml
columnsToDuplicate:
  employee_id: employee_id_original
  manager_id: manager_id_backup
```

#### See Also

- Rule structure is specified in [`ColumnarRules`](../../java/gateway-core/src/main/java/com/avaulta/gateway/rules/ColumnarRules.java).

### Record-Oriented Formats (RecordRules)

_As of Oct 2023, this is a **beta** feature_

`RecordRules` parses files as records, presuming the specified format. It performs transforms in order on each record to sanitize your data, and serializes the result back to the specified format.

eg.

```yaml
format: NDJSON
transforms:
  - redact: "$.summary"
  - pseudonymize: "$.email"
```

Each `transform` is a map from transform type --> to a JSONPath to which the transform should be applied. The JSONPath is evaluated from the root of each record in the file.

The above example rules applies two transforms. First, it redacts `$.summary` - the `summary` field at the root at of the record object. Second, it pseudonymizes `$.email` - the `email` field at the root of the record object.

`transforms` itself is an ordered-list of transforms. The transforms should be applied in order.

CSV format is also supported, but in effect is converted to a simple JSON object before rules are applied; so JSON paths in transforms should all be single-level; eg, `$.email` to refer to the `email` column in the CSV.

#### See Also

- Rule structure is specified in [`RecordRules`](../..java/gateway-core/src/main/java/com/avaulta/gateway/rules/RecordRules.java).

### Mixing File Formats

_As of Oct 2023, this feature is in **beta** and may change in backwards incompatible ways_

You can process multiple file formats through a single proxy instance using `MultiTypeBulkDataRules`.

These rules are structured with a field `fileRules`, which is a map from parameterized path template within the "input" bucket to one of the above rule types (`RecordRules`,`ColumnarRules`) to be applied to files matching that path template.

```yaml
fileRules:
  /export/{week}/index_{shard}.ndjson:
    format: "NDJSON"
    transforms:
      - redact: "$.foo"
      - pseudonymize: "$.bar"
  /export/{week}/data_{shard}.csv:
    columnsToPseudonymize:
      - "email"
    delimiter: ","
    pseudonymFormat: "JSON"
```

Path templates are evaluated against the incoming file (object) path in order, and the first match is applied to the file. If no templates match the incoming file, it will not be processed.

## Configuration

Worklytics' provided Terraform modules include default rules for expected formats for `hris`, `survey`, and `badge` data connectors.

If your input data does not match the expected formats, you can customize the rules in one of the following ways.

_NOTE: The configuration approaches described below utilized Terraform variables as provided by our gcp and aws template examples. Other examples may not support these variables; please consult the `variables.tf` at the root of your configuration. If you are directly using Worklytics' Terraform modules, you can consult the `variables.tf` in the module directory to see if these variables are exposed._

### Custom Rules of Predefined Bulk Connector (preferred)

You can override the rules used by the predefined bulk connectors (eg `hris`, `survey`, `badge`) by filling the `custom_bulk_connector_rules` variable in your Terraform configuration.

This variable is a map from connector ID --> rules, with the rules encoded in HCL format (rather than YAML as shown above). An illustrative example:

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

This approach ONLY supports `ColumnarRules`

### Custom Bulk Connector

Rather than enabling one of the predefined bulk connectors providing in the `worklytics-connector-specs` Terraform module, you can specify a custom connector from scratch, including your own rules.

This approach is less convenient than the previous one, as TODO documentation and deep-links for connecting your data to Worklytics will not be generated.

To create a Custom Bulk Connector, use the `custom_bulk_connectors` variable in your Terraform configuration, for example:

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

The above example is for `ColumnarRules`.

### Direct Configuration of RULES

You can directly modify the `RULES` environment variable on the Psoxy instance, by directly editing your instance's environment via your hosting provider's console or CLI. In this case, the rules should be encoded in YAML format, such as:

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

Alternatively, you can remove the environment variable from your instance, and instead configure a `RULES` value in the "namespace" of your instance, in the AWS Parameter Store or GCP Secret Manager (as appropriate for your hosting provider).

This approach is useful for testing, but note that if you later run `terraform apply` again, any changes you make to the environment variable may be overwritten by Terraform.

### Troubleshooting

If you encounter issues processing your files, check the logs of the Psoxy instance. The logs will give some indication of what went wrong, and may help you identify the issue.

#### Error: java.lang.IllegalArgumentException: Mapping for employee_id not found

**Causes**: The column specified in `columnsToPseudonymize` is not present in the input data or contains empty values. Any column specified in `columnsToPseudonymize` must be present in the input data.

**Solution**: Regenerate your input file removing empty values for mandatory columns.

#### Error: java.lang.OutOfMemoryError

**Causes**: The file size is too large for the Psoxy instance to process, likely in AWS Lambda in proxy versions prior to v0.4.54.

**Solutions:**

1. Use compression in the file (see [Compression](#compression)); if already compressed, then:
2. Split the file into smaller files and process them separately
3. (AWS only) Update the proxy version to v0.4.55 or later
4. (AWS only) If in v0.4.55 or later, process the files one by one or increase the ephemeral storage allocated to the Lambda function (see https://aws.amazon.com/blogs/aws/aws-lambda-now-supports-up-to-10-gb-ephemeral-storage/)
