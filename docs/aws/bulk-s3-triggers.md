# Bulk input triggers (S3)

On AWS bulk connectors, every new object in the connector **input** bucket can invoke the Psoxy Lambda. If your upstream process lands many files in that bucket but only some should be sanitized, configure optional trigger filters in Terraform. Non-matching uploads remain in the bucket; they simply do not invoke Psoxy.

Rules in your Psoxy configuration (path patterns, file types, etc.) still decide whether an invoked object is **processed** or **skipped** after the Lambda runs. Trigger filters only reduce how often the Lambda is invoked.

## Native S3 notification (prefix + suffix)

Set `file_prefix` and/or `file_suffix` on the connector (custom bulk connectors) or in `custom_bulk_connector_arguments` (for prebuilt connectors such as `hris`). These map to the S3 notification `filter_prefix` / `filter_suffix` for `s3:ObjectCreated` events. If both are set, the object key must match **both**.

Example for a prebuilt connector:

```hcl
custom_bulk_connector_arguments = {
  hris = {
    file_prefix = "worklytics/inbound/"
    file_suffix = ".csv"
  }
}
```

## EventBridge (wildcard pattern)

Set `file_pattern` instead of prefix/suffix when you need EventBridge-style wildcards in the object key (for example `exports/*/psoxy/*.csv`). Terraform provisions S3 EventBridge notifications plus an EventBridge rule that invokes the bulk Lambda when `file_pattern` is set. You cannot combine `file_pattern` with `file_prefix` or `file_suffix`; Terraform fails at plan time if both are set.

Example:

```hcl
custom_bulk_connector_arguments = {
  hris = {
    file_pattern = "worklytics/inbound/*.csv"
  }
}
```

Example on a custom bulk connector:

```hcl
custom_bulk_connectors = {
  my_vendor_export = {
    source_kind = "my_vendor_export"
    file_prefix = "exports/psoxy/"
    file_suffix = ".csv.gz"
    rules = {
      columnsToPseudonymize = ["employee_id"]
    }
  }
}
```

See [AWS S3 event notification filters](https://docs.aws.amazon.com/AmazonS3/latest/userguide/notification-how-to-filtering.html) and [EventBridge event patterns](https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-event-patterns.html).
