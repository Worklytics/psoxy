# HRIS / HCM Data

The Psoxy HRIS (human resource information system) connector is intended to sanitize data exported
from an HRIS/HCM system which you intend to transfer to Worklytics. The expected format is a CSV
file, as defined in the documentation for import data (obtain from Worklytics).

See: https://docs.worklytics.co/knowledge-base/connectors/bulk-data/hris-snapshots

The default proxy rules for `hris` will pseudonymize `EMPLOYEE_ID`, `EMPLOYEE_EMAIL`, and `MANAGER_ID`
columns. If you ALSO include the `MANAGER_EMAIL` column, you must use custom rules by adding
something like the following in your `terraform.tfvars`:

```hcl
custom_bulk_connector_rules = {
    hris = {
        columnsToPseudonymize = [
            "EMPLOYEE_ID",
            "EMPLOYEE_EMAIL",
            "MANAGER_ID",
            "MANAGER_EMAIL"
        ]
    }
}
```

