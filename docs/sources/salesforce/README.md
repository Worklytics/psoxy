# Salesforce

## Examples

- [Example Rules](salesforce.yaml)
- Example Data : [original/accounts.json](example-api-responses/original/accounts.json) |
  [sanitized/accounts.json](example-api-responses/sanitized/accounts.json)

See more examples in the `docs/sources/salesforce/example-api-responses` folder
of the [Psoxy repository](https://github.com/Worklytics/psoxy).

## Steps to Connect

Before running the example, you have to populate the following variables in terraform:

- `salesforce_domain`. This is the [domain](https://help.salesforce.com/s/articleView?id=sf.faq_domain_name_what.htm&type=5) your instance is using.
- `salesforce_example_account_id`: An example of any account id; this is only applicable for example calls.

1. Create a [Salesforce application + client credentials flow](https://help.salesforce.com/s/articleView?language=en_US&id=sf.remoteaccess_oauth_client_credentials_flow.htm&type=5)with following permissions:

    - Manage user data via APIs (`api`)
    - Access Connect REST API resources (`chatter_api`)
    - Perform requests at any time (`refresh_token`, `offline_access`)
    - Access unique user identifiers (`openid`)
    - Access Lightning applications (`lightning`)
    - Access content resources (`content`)
    - Perform ANSI SQL queries on Customer Data Platform data (`cdp_query_api`)

   Apart from Salesforce instructions above, please review the following:

        - "Callback URL" MUST be filled; can be anything as not required in this flow, but required
          to be set by Salesforce.
        - Application MUST be marked with "Enable Client Credentials Flow"
        - You MUST assign a user for Client Credentials, be sure:
            - you associate a "run as" user marked with "API Only Permission"
            - The policy associated to the user MUST have the following Administrative Permissions
              enabled:
                - API Enabled
                - APEX REST Services
        - The policy MUST have the application marked as "enabled" in "Connected App Access".
          Otherwise requests will return 401 with INVALID_SESSION_ID

  The user set for "run as" on the connector should have, between its `Permission Sets` and`Profile`, the permission of `View All Data`. This is required to support the queries used to retrieve [Activity Histories](https://developer.salesforce.com/docs/atlas.en-us.object_reference.meta/object_reference/sforce_api_objects_activityhistory.htm) by _account id_.

2. Once created, open "Manage Consumer Details"
3. Update the content of `PSOXY_SALESFORCE_CLIENT_ID` from Consumer Key and
   `PSOXY_SALESFORCE_CLIENT_SECRET` from Consumer Secret
4. Finally, we recommend to run `test-salesforce` script with all the queries in the example to ensure the expected information covered by rules can be obtained from Salesforce API. Some test calls may fail with a 400 (bad request) response. That is something expected if parameters requested on the query are not available (for example, running a SOQL query with fields that are NOT present in your model will force a 400 response from Salesforce API). If that is the case, a double check in the function logs can be done to ensure that this is the actual error happening, you should see an error like the following one:
```json
WARNING: Source API Error [{     "message": "\nLastModifiedById,NumberOfEmployees,OwnerId,Ownership,ParentId,Rating,Sic,Type\n                ^\nERROR at Row:1:Column:136\nNo such column 'Ownership' on entity 'Account'. If you are attempting to use a custom field, be sure to append the '__c' after the custom field name. Please reference your WSDL or the describe call for the appropriate names.",     "errorCode": "INVALID_FIELD"      }]
```
   In that case, removing from the query the fields `LastModifiedById,NumberOfEmployees,OwnerId, Ownership,ParentId,Rating,Sic,Type` will fix the issue`.

   However, if running any of the queries you receive a 401/403/500/512. A 401/403 it might be related to some misconfiguration in the Salesforce Application due lack of permissions; a 500/512 it could be related to missing parameter in the function configuration (for example, a missing value for `salesforce_domain` variable in your terraform vars)


NOTE: derived from [worklytics-connector-specs](../../../infra/modules/worklytics-connector-specs/main.tf); refer to that for definitive information.
