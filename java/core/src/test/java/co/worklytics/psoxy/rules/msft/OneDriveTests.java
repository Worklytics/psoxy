package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;

import java.util.stream.Stream;

public class OneDriveTests extends JavaRulesTestBaseCase {

    @Getter
    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.ONE_DRIVE;

    @Override
    public RulesTestSpec getRulesTestSpec() {
        return RulesTestSpec.builder()
                .sourceFamily("microsoft-365")
                .sourceKind("msft-onedrive")
                .build();
    }

    @Override
    public Stream<InvocationExample> getExamples() {
        String apiVersion = "v1.0";
        String baseEndpoint = "https://graph.microsoft.com/" + apiVersion;
        String userId = "48d31887-5fad-4d73-a9f5-3c356e68a038";
        String groupId = "fbe2bf47-16c8-47cf-b4a5-4b9b187c508b";
        String siteId = "contoso.sharepoint.com,a1b2c3d4-e5f6-7890-abcd-ef1234567890,b2c3d4e5-f6a1-2345-bcde-f12345678901";
        String itemId = "01BYE5RZ6QN3ZWBTUFOFD3GSPGOHDJD36K";

        return Stream.of(
                InvocationExample.of(baseEndpoint + "/users/" + userId + "/drive/root/delta", "Users_drive_delta_" + apiVersion + ".json"),
                InvocationExample.of(baseEndpoint + "/groups/" + groupId + "/drive/root/delta", "Users_drive_delta_" + apiVersion + ".json"),
                InvocationExample.of(baseEndpoint + "/sites/" + siteId + "/drive/root/delta", "Users_drive_delta_" + apiVersion + ".json"),
                InvocationExample.of(baseEndpoint + "/users/" + userId + "/drive/items/" + itemId + "/versions", "Drive_items_versions_" + apiVersion + ".json"),
                InvocationExample.of(baseEndpoint + "/groups/" + groupId + "/drive/items/" + itemId + "/versions", "Drive_items_versions_" + apiVersion + ".json"),
                InvocationExample.of(baseEndpoint + "/sites/" + siteId + "/drive/items/" + itemId + "/versions", "Drive_items_versions_" + apiVersion + ".json")
        );
    }
}
