# Metrics Data

 - [Example data](metrics-example.csv)
 - [Example sanitized data](metrics-example-sanitized.csv)
 - [Example rules](metrics.yaml)

The default `metrics` proxy rules pseudonymize the `EMPLOYEE_ID` column. If your metrics data does
not match the expected schema above, you can customize the proxy rules to perform some basic ETL-like
transforms along the lines of the following:

```hcl
custom_bulk_connector_rules = {
    metrics = {
        columnsToPseudonymize = [
            "EMPLOYEE_ID"
        ],
        columnsToRename = {
            "employeeId" = "EMPLOYEE_ID",
            "metricName" = "KEY",
            "metricValue" = "VALUE"
        }
    }
}

```
