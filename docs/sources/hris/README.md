# HRIS / HCM Data

The Psoxy HRIS (human resource information system) connector is intended to sanitize data exported
from an HRIS/HCM system which you intend to transfer to Worklytics. The expected format is a CSV
file, as defined in the documentation for import data (obtain from Worklytics).

See: [https://docs.worklytics.co/knowledge-base/connectors/bulk-data/hris-snapshots](https://docs.worklytics.co/knowledge-base/connectors/bulk-data/hris-snapshots)

The default proxy rules for `hris` will pseudonymize `EMPLOYEE_ID`, `EMPLOYEE_EMAIL`, `MANAGER_ID` -
as well as a `MANAGER_EMAIL` column if it's included.

If your HRIS data does not match the expected schema above, you can customize the proxy rules to
perform some basic ETL-like transforms on the data within the proxy itself:

```hcl
custom_bulk_connector_rules = {
    hris = {
        columnsToPseudonymize = [
            "EMPLOYEE_ID",
            "EMPLOYEE_EMAIL",
            "MANAGER_ID"
        ],
        columnsToPseudonymizeIfPresent = [
            "MANAGER_EMAIL"
        ],
        columnsToRedact = [
            "SALARY",
            "BANK_ACCOUNT_NUMBER"
        ],
        columnsToRename = {
            "employeeId" = "EMPLOYEE_ID",
        }
    }
}
```

