package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;

import java.util.stream.Stream;

public class OneDrive_NoAppIds_Tests extends JavaRulesTestBaseCase {

    @Getter
    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.ONE_DRIVE_NO_APP_IDS;

    @Override
    public RulesTestSpec getRulesTestSpec() {
        return RulesTestSpec.builder()
                .sourceFamily("microsoft-365")
                .sourceKind("msft-onedrive")
                .rulesFile("msft-onedrive_no-app-ids")
                .exampleSanitizedApiResponsesPath("example-api-responses/sanitized_no-app-ids/")
                .checkUncompressedSSMLength(false)
                .build();
    }

    @Override
    public Stream<InvocationExample> getExamples() {
        String apiVersion = "v1.0";
        String baseEndpoint = "https://graph.microsoft.com/" + apiVersion;
        String userId = "p~JuB1uFI_rtVS0Ygtc3m4uxhEiLI-6vn5ySKma20etlGvAJvlFOlnYuRejZSdIm5tmHzio-TdKzazWRwL50vNeFravJETR0l1WAvE219Jwug";
        String groupId = "fbe2bf47-16c8-47cf-b4a5-4b9b187c508b";
        String driveId = "b!-RIj2DuyvEyV1T4NlOaMHk8XkS_I8MdFlUCq1BlcjgmhRfAj3-Z8RY2VpuvV_tpd";
        String itemId = "01BYE5RZ6QN3ZWBTUFOFD3GSPGOHDJD36K";

        return Stream.of(
            // /v1.0/users - no query params, and with all allowed query params
            InvocationExample.of(baseEndpoint + "/users", "users.json"),
            InvocationExample.of(baseEndpoint + "/users?$top=999&$select=id,mail,employeeId,otherMails,proxyAddresses&$skiptoken=abcXYZ123&$orderby=id&$count=true", "users.json"),
            // /v1.0/users - $filter, as used to partition user enumeration across parallel jobs by userPrincipalName prefix
            InvocationExample.of(baseEndpoint + "/users?$filter=startswith(userPrincipalName,'a')&$top=999&$select=id,mail,employeeId,otherMails,proxyAddresses", "users.json"),
            // /v1.0/groups - no query params, and with all allowed query params
            InvocationExample.of(baseEndpoint + "/groups", "groups.json"),
            InvocationExample.of(baseEndpoint + "/groups?$top=999&$select=id,mail&$skiptoken=abcXYZ123&$orderby=id&$count=true", "groups.json"),
            // /v1.0/users/{userId}/drives - no query params, and with all allowed query params
            // (fixture's @odata.nextLink embeds the raw, un-pseudonymized AAD user id that Graph
            // resolves the path to; it must be re-tokenized so a client following the link isn't
            // blocked by pathParameterSchemas requiring a reversible-pseudonym userId)
            InvocationExample.of(baseEndpoint + "/users/" + userId + "/drives", "list_user_drives.json"),
            InvocationExample.of(baseEndpoint + "/users/" + userId + "/drives?$select=id,driveType,system&$skiptoken=abcXYZ123&$top=999&$orderby=id&$expand=root", "list_user_drives.json"),
            // /v1.0/groups/{groupId}/drives - no query params, and with all allowed query params
            InvocationExample.of(baseEndpoint + "/groups/" + groupId + "/drives", "list_groups_drives.json"),
            InvocationExample.of(baseEndpoint + "/groups/" + groupId + "/drives?$select=id,driveType,system&$skiptoken=abcXYZ123&$top=999&$orderby=id&$expand=root", "list_groups_drives.json"),
            // /v1.0/drives/{driveId}/root/delta - no query params, and with all allowed query params
            InvocationExample.of(baseEndpoint + "/drives/" + driveId + "/root/delta", "get_drive_delta.json"),
            // real pagination continuation shape confirmed via live testing against the Graph API
            // (a "?token=..." query param, matching MSFT's own docs example)
            InvocationExample.of(baseEndpoint + "/drives/" + driveId + "/root/delta?token=abcXYZ123&", "get_drive_delta.json"),
            // the OTHER real continuation shape, from this fixture's own captured @odata.nextLink
            // (an OData function-call path segment -- rules must accept both)
            InvocationExample.of(baseEndpoint + "/drives/" + driveId + "/root/delta(token=1230919asd190410jlka)", "get_drive_delta.json"),
            // /v1.0/drives/{driveId}/items/{itemId}/activities - no query params allowed by rules
            InvocationExample.of(baseEndpoint + "/drives/" + driveId + "/items/" + itemId + "/activities", "list_itemActivity.json"),
            InvocationExample.of(baseEndpoint + "/drives/" + driveId + "/items/" + itemId + "/activities?$expand=driveItem", "list_itemActivity.json"),
            InvocationExample.of(baseEndpoint + "/drives/" + driveId + "/items/" + itemId + "/activities?$skiptoken=some_token", "list_itemActivity.json"),

            // /v1.0/drives/{driveId}/activities - no query params allowed by rules
            InvocationExample.of(baseEndpoint + "/drives/" + driveId + "/activities", "list_driveActivity.json"),
            InvocationExample.of(baseEndpoint + "/drives/" + driveId + "/activities?$expand=driveItem", "list_driveActivity.json")
        );
    }
}
