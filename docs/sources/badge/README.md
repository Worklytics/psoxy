# Badge Swipe Data

Psoxy can pseudonymize badge swipe data for ingestion into Worklytics.

See [https://docs.worklytics.co/knowledge-base/connectors/bulk-data/badge](https://docs.worklytics.co/knowledge-base/connectors/bulk-data/badge)

The default proxy rules for `badge` will pseudonymize `EMPLOYEE_ID`. If your data set does not match
the schema expected by Worklytics, you can adapt it by specifying some custom transforms within
the proxy itself:

```hcl
custom_bulk_connector_rules = {
    badge = {
        columnsToPseudonymize = [
            "EMPLOYEE_ID"
        ],
        columnsToRename = {
            "employeeId" = "EMPLOYEE_ID",
            "timestamp" = "SWIPE_DATE",
            "location" = "BUILDING_ID"
            "homeOffice" = "BUILDING_ASSIGNED"
        }
    }
}
```
