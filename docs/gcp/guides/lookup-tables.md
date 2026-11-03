# Lookup Tables with GCP

If you use Psoxy to send pseudonymized data to Worklytics and later wish to re-identify the data that you export from Worklytics to your premises, you'll need a lookup table in your data warehouse to JOIN with that data.

Our `gcp-host` Terraform module, as used in our [Psoxy GCP Example](https://github.com/Worklytics/psoxy-example-gcp/tree/main), provides a variable `lookup_tables` to control generation of these lookup tables.

Populating this variable will generate another version of your HRIS data (aside from the one exposed to Worklytics) which you can then import back to your data warehouse.

To enable it, add the following to your `terraform.tfvars` file:

```hcl
lookup_tables = {
  "hris-lookup" = {
    source_connector_id = "hris",
    sanitized_accessor_principals = [
      # ADD LIST OF GCP PRINCIPALS HERE
      # eg, "serviceAccount:datawarehouse-ingestion@my-project.googleserviceaccount.com"
    ],
    join_key_column  = "EMPLOYEE_ID"
    pseudonym_format = "URL_SAFE_HASH_ONLY" # recommended for JOIN with Worklytics exports (see below)
  }
}
```

Set `join_key_column` to the join column **as named in your HRIS CSV** (for the standard schema below, `EMPLOYEE_ID`). Terraform names the pseudonym column `{join_key_column}_pseudonym` (for example `EMPLOYEE_ID_pseudonym`).

Set `pseudonym_format` on each lookup table entry to control encoding of `{join_key_column}_pseudonym`. Use `URL_SAFE_HASH_ONLY` so values match `employeeId` in [Worklytics weekly aggregates](https://docs.worklytics.co/knowledge-base/data-export/weekly-aggregates) exports (base64url SHA-256, no `t~` prefix). `URL_SAFE_TOKEN` is also supported if your export uses that encoding instead. If you omit `pseudonym_format`, the proxy falls back to its historical columnar default (`JSON`), which is **not** suitable for joining to Worklytics exports—set `pseudonym_format` explicitly for new deployments. A future `gcp-host` release will change the Terraform default to `URL_SAFE_HASH_ONLY`.

In `sanitized_accessor_principals`, add the name of whatever GCP principal running ingestion of your lookup table from GCS to your data warehouse will assume. You can add additional principals as needed. Alternatively, you can use an IAM policy created outside our Terraform module to grant access to the lookup table CSVs within the bucket.

After you apply this configuration, the lookup table will be generated in a GCS bucket. The bucket ID will be shown in the Terraform output:

```shell
Outputs:

lookup_tables = {
  "hris-lookup" = "psoxy-lookup-hris-20221025202138363600000001"
}
```

Use the bucket name shown in your output to build import pipeline to your data warehouse.

If your input file follows the [standard HRIS schema](https://docs.worklytics.co/knowledge-base/connectors/bulk-data/hris-snapshots) for Worklytics, it will have `SNAPSHOT,EMPLOYEE_ID,EMPLOYEE_EMAIL,JOIN_DATE,LEAVE_DATE,MANAGER_ID` columns, at minimum.

Every time a new hris snapshot is uploaded to the hris `-input` bucket, TWO copies of it will be created: a sanitized copy in the bucket accessible Worklytics, and the lookup variant in the lookup bucket referenced above (not accessible to Worklytics).

The lookup table CSV is **not** limited to only two columns. Terraform generates lookup rules that emit the HRIS columns from your source file (unless you restrict them; see [Advanced](#advanced)), and **add** a `{join_key_column}_pseudonym` column by duplicating your join key and pseudonymizing that copy.

With `join_key_column = "EMPLOYEE_ID"`, `pseudonym_format = "URL_SAFE_HASH_ONLY"`, and `columns_to_include = []` (see [Minimal lookup table](#minimal-lookup-table-join-key-columns-only)), each row has only:

| `EMPLOYEE_ID` | `EMPLOYEE_ID_pseudonym` |
| --- | --- |
| `4` | `T7Yc2M2WFvUjLr5yA9qyClyh7vdiXcwRWK23GLEensI` |
| `3` | `ibZQXyijkJVHRn1-VuCLYfDLr8gr17MHOATQU4L4VFE` |

`EMPLOYEE_ID` is the original HRIS identifier. `EMPLOYEE_ID_pseudonym` is the same value pseudonymized with `URL_SAFE_HASH_ONLY` (base64url SHA-256, no `t~` prefix)—the form that appears as `employeeId` in [Worklytics weekly aggregates](https://docs.worklytics.co/knowledge-base/data-export/weekly-aggregates) exports and the column you JOIN on.

If you load this into your Data Warehouse, you can JOIN it with the data you export from Worklytics.

Eg, assuming you've exported the [Worklytics Weekly aggregates data set](https://docs.worklytics.co/knowledge-base/data-export/weekly-aggregates) to your data warehouse, load the files from bucket above into a table named `lookup_hris`.

Then the following query will give re-identified aggregate data:

```sql
SELECT
    acmecom_ANONYMIZED_AGGREGATES.* EXCEPT (employeeId),
    lookup_hris.EMPLOYEE_ID AS employeeId
FROM
    acmecom_ANONYMIZED_AGGREGATES JOIN lookup_hris
        ON acmecom_ANONYMIZED_AGGREGATES.employeeId = lookup_hris.EMPLOYEE_ID_pseudonym
```

The `employeeId` column in the result set will be the original employee ID from your HRIS system.

### Security and Privacy Considerations

If your HRIS employee ID column is considered PII, then the lookup table and any re-identified data exports you use it to produce should be handled as Personal data, according to your policies, as these now reference readily identifiable Natural Persons.

If you wish limit re-identification to a subset of your data, you can use additional columns present in your HRIS csv to do so, for example:

```sql
WITH allowed_for_reidentification AS (
    SELECT
        *
    FROM
        lookup_hris
    WHERE
        department = 'Sales'
)
SELECT
    acmecom_ANONYMIZED_AGGREGATES.* EXCEPT (employeeId),
    allowed_for_reidentification.EMPLOYEE_ID AS employeeId
FROM
    acmecom_ANONYMIZED_AGGREGATES JOIN allowed_for_reidentification
        ON acmecom_ANONYMIZED_AGGREGATES.employeeId = allowed_for_reidentification.EMPLOYEE_ID_pseudonym
```

### Advanced

You can add some addition configuration to the `lookup_tables` variable to control the behavior of the lookup table generation:
   - `expiration_days` - to expire the lookup table files after this number of days
   - `output_bucket_name` - to override the value of the bucket name that the Terraform modules will create, if you have your own naming convention
   - `columns_to_include` - optional on `lookup_tables`; controls column filtering for lookup output (see below)

#### `columns_to_include` behavior

This field is exposed on the `lookup_tables` variable in `gcp-host` (and in our GCP examples as `lookup_tables` in `terraform.tfvars`).

- **Omitted or `null`:** no `columnsToInclude` rule is applied, so lookup output keeps all HRIS columns (plus the generated `{join_key_column}_pseudonym` column).
- **Any non-null list:** the module turns on `columnsToInclude` and **always** adds `join_key_column` and `{join_key_column}_pseudonym` to whatever you list. Your list is therefore **extra** HRIS columns to keep alongside that join pair, not the full output schema.

GCP lookup tables do **not** add `EMPLOYEE_ID_ORIG`. The join pair is cleartext `EMPLOYEE_ID` plus `EMPLOYEE_ID_pseudonym` (when `join_key_column = "EMPLOYEE_ID"`). That differs from the [AWS lookup pattern](../../aws/guides/lookup-tables.md), which pseudonymizes `EMPLOYEE_ID` and adds `EMPLOYEE_ID_ORIG` for the original value.

#### Minimal lookup table (join key columns only)

To emit only `EMPLOYEE_ID` and `EMPLOYEE_ID_pseudonym` (as in the table above), enable filtering with a non-null `columns_to_include` and list **no** extra HRIS columns. An empty list means “join pair only”; you do not list the join or pseudonym columns in `terraform.tfvars`—the module injects them into the sanitization rules from `join_key_column`.

```hcl
lookup_tables = {
  "hris-lookup" = {
    source_connector_id = "hris",
    join_key_column     = "EMPLOYEE_ID",
    pseudonym_format    = "URL_SAFE_HASH_ONLY",
    columns_to_include  = [], # no extra HRIS columns; module still adds EMPLOYEE_ID + EMPLOYEE_ID_pseudonym
    sanitized_accessor_principals = [
      # ...
    ],
  }
}
```

To keep the join pair plus other HRIS fields (for example, to filter re-identification by department), list only those extras:

```hcl
    columns_to_include = ["department"]
```
