# Miro Audit Log - Miro AI

Psoxy can pseudonymize Miro AI from [Miro Audit Log CSV](https://help.miro.com/hc/en-us/articles/360017571434-Audit-logs#h_01J7EY4E0F67EFTRQ7BT688HW0) data for ingestion into Worklytics.

See [https://docs.worklytics.co/knowledge-base/connectors/bulk-data/miro-ai-bulk](https://docs.worklytics.co/knowledge-base/connectors/bulk-data/miro-ai-bulk)

The default proxy rules for `miro-ai-bulk` will pseudonymize `Actor` and `Team Name`. Fields like `IP Address`, `Actor Name`
and `Affected Object` will be redacted. If your data set does not match
the schema expected by Worklytics, you can adapt it by specifying some custom transforms within
the proxy itself:

```hcl
custom_bulk_connector_rules = {
    "miro-audit-log-ai-bulk" = {
        source_kind               = "miro-ai",
        worklytics_connector_id   = "bulk-import-psoxy"
        worklytics_connector_name = "Bulk Import - Psoxy"
        display_name              = "Miro Audit Log - Miro AI Bulk"
        rules = {
            columnsToPseudonymize = [
                "Actor",
                "Team Name"
            ],
            columnsToRedact = [
                "IP Address",
                "Actor Name",
                "Affected Object"
            ]
        }
        settings_to_provide = {
            "Parser" = "miro-audit-log-ai"
        }
    }
}
```
