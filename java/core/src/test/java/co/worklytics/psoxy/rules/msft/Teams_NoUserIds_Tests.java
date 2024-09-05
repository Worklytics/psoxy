package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.Rules2;
import jdk.jfr.Description;
import lombok.Getter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Stream;

public class Teams_NoUserIds_Tests extends JavaRulesTestBaseCase {

    @Getter
    final Rules2 rulesUnderTest = PrebuiltSanitizerRules.MS_TEAMS_NO_USER_ID;


    @Getter
    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
            .sourceFamily("microsoft-365")
            .defaultScopeId("azure-ad")
            .sourceKind("msft-teams")
            .rulesFile("msft-teams_no-userIds")
            .exampleSanitizedApiResponsesPath("example-api-responses/sanitized_no-userIds/")
            .build();

    @Test
    @Description("Test endpoint:" + PrebuiltSanitizerRules.MS_TEAMS_PATH_TEMPLATES_TEAMS)
    public void teams() {
        String endpoint = "https://graph.microsoft.com/" + "v1.0" + "/teams";
        String jsonResponse = asJson("Teams_" + "v1.0" + ".json");

        String sanitized = sanitize(endpoint, jsonResponse);
        assertRedacted(sanitized,
                "@odata.type", "#microsoft.graph.associatedTeamInfo"
        );
        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @Test
    @Description("Test endpoint: " + PrebuiltSanitizerRules.MS_TEAMS_PATH_TEMPLATES_TEAMS_ALL_CHANNELS)
    public void teams_allChannels() {
        String teamId = "172b0cce-e65d-44ce-9a49-91d9f2e8493a";
        String endpoint = "https://graph.microsoft.com/" + "v1.0" + "/teams/" + teamId + "/allChannels";
        String jsonResponse = asJson("Teams_allChannels_" + "v1.0" + ".json");

        String sanitized = sanitize(endpoint, jsonResponse);
        assertRedacted(sanitized,
                "@odata.id", "https://graph.microsoft.com/v1.0/tenants/b3246f44-b4gb-4627-96c6-25b18fa2c910/teams/893075dd-2487-4122-925f-022c42e20265/channels/19:561fbdbbfca848a484f0a6f00ce9dbbd@thread.tacv2"
        );
        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @Test
    @Description("Test endpoint: " + PrebuiltSanitizerRules.MS_TEAMS_PATH_TEMPLATES_USERS_CHATS)
    public void users_chats() {
        String userId = "p~JuB1uFI_rtVS0Ygtc3m4uxhEiLI-6vn5ySKma20etlGvAJvlFOlnYuRejZSdIm5tmHzio-TdKzazWRwL50vNeFravJETR0l1WAvE219Jwug";
        String endpoint = "https://graph.microsoft.com/" + "v1.0" + "/users/" + userId + "/chats";
        String jsonResponse = asJson("Users_chats_" + "v1.0" + ".json");

        String sanitized = sanitize(endpoint, jsonResponse);
        assertRedacted(sanitized,
                "@odata.context",
                "https://graph.microsoft.com/v1.0/$metadata#chats",
                "48d31887-5fad-4d73-a9f5-3c356e68a038",
                "@odata.count"
        );

        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @Test
    @Description("Test endpoint: " + PrebuiltSanitizerRules.MS_TEAMS_PATH_TEMPLATES_USERS_CHATS)
    public void users_chats_should_block_user_id() {
        String userId = "8b081ef6-4792-4def-b2c9-c363a1bf41d5";
        String endpoint = "https://graph.microsoft.com/" + "v1.0" + "/users/" + userId + "/chats";

        assertUrlBlocked(endpoint);
    }

    @Test
    @Description("Test endpoint: " + PrebuiltSanitizerRules.MS_TEAMS_PATH_TEMPLATES_TEAMS_CHANNELS_MESSAGES)
    public void teams_channels_messages() {
        String teamId = "172b0cce-e65d-44ce-9a49-91d9f2e8493a";
        String channelId = "19:4a95f7d8db4c4e7fae857bcebe0623e6@thread.tacv2";
        String endpoint = "https://graph.microsoft.com/" + "v1.0" + "/teams/" + teamId + "/channels/" + channelId + "/messages";
        String jsonResponse = asJson("Teams_channels_messages_" + "v1.0" + ".json");

        String sanitized = sanitize(endpoint, jsonResponse);
        assertPseudonymized(sanitized, "8ea0e38b-efb3-4757-924a-5f94061cf8c2",
                "1fb8890f-423e-4154-8fbf-db6809bc8756");
        assertRedacted(sanitized,
                "@odata.context", "https://graph.microsoft.com/v1.0/$metadata#teams('fbe2bf47-16c8-47cf-b4a5-4b9b187c508b')/channels('19%3A4a95f7d8db4c4e7fae857bcebe0623e6%40thread.tacv2')/messages",
                "@odata.count"
        );

        Collection<String> oDataUrl = Arrays.asList(
                "https://graph.microsoft.com/v1.0/teams/fbe2bf47-16c8-47cf-b4a5-4b9b187c508b/channels/19:4a95f7d8db4c4e7fae857bcebe0623e6@thread.tacv2/messages?$skiptoken=%5b%7B%22token%22%3a%22%2bRID%3a~vpsQAJ9uAC047gwAAACcBQ%3d%3d%23RT%3a1%23TRC%3a20%23RTD%3aAyAER1ygxSHVHGAn2S99BTI6OzViOjZnOGU5ZWM1ZDVmOGdiZjk2OGNkZmNmMTczNGY3QXVpc2ZiZS91YmR3MzwyNzIyNDY2OTU0NTg6AA%3d%3d%23ISV%3a2%23IEO%3a65551%23QCF%3a3%23FPC%3aAggEAAAAcBYAABUFAADQKgAABAAAAHAWAAACALu4GwAAAHAWAAACAPSTMwAAAHAWAACaAFWa84BXgQKAEIAMgBaAE4AUgAuAAoAIwAIgACAAAiAACAABACCAAAEVgBSAI4AYgA%2bAGQAEEAAQAAEABACAAAIEEBBAACAYgB%2bAH4AbgBqACoAHwAICCBAEEIAAAgEQAACAIoAZgB2ADoAMgAKAPoAZgB2AJoAXgBIAgiAAQUqLF4AJgALACARAgBCACoAfgB6AIwABgYCQAAFXAAAAcBYAAAYA%2f50ZgGeEXwAAAHAWAAAEAPaBS4V7AAAAcBYAAAIA1aSJAAAAcBYAAAIAtLmbAAAAcBYAAAIAqKXdAAAAcBYAAAQAppUugOMAAABwFgAABADQoAWA6wAAAHAWAAAEABGl94M5AAAA0CoAAAYA6pF7iYOBaQIAANAqAAAcAEUPAMAAMAACAQCBAHQAADDAgCAAQgByAQAzUJDRBAAA0CoAAAQAETwKAA4FAADQKgAAAgBekRUFAADQKgAAHAB2pQCABYAMgJeAH4ATgAGAvIIIgASABIAFgCWA%22%2c%22range%22%3a%7B%22min%22%3a%2205C1D79B33ADE4%22%2c%22max%22%3a%2205C1D7A52F89EC%22%7D%7D%5d"
        );

        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @Test
    @Description("Test endpoint: " + PrebuiltSanitizerRules.MS_TEAMS_PATH_TEMPLATES_TEAMS_CHANNELS_MESSAGES_DELTA)
    public void teams_channels_messages_delta() {
        String teamId = "172b0cce-e65d-44ce-9a49-91d9f2e8493a";
        String channelId = "channelId";
        String endpoint = "https://graph.microsoft.com/" + "v1.0" + "/teams/" + teamId + "/channels/" + channelId + "/messages/delta";
        String jsonResponse = asJson("Teams_channels_messages_delta_" + "v1.0" + ".json");

        String sanitized = sanitize(endpoint, jsonResponse);
        assertPseudonymized(sanitized, "8ea0e38b-efb3-4757-924a-5f94061cf8c2");
        assertRedacted(sanitized,
                "@odata.context", "https://graph.microsoft.com/v1.0/$metadata#Collection(chatMessage)"
        );

        Collection<String> oDataUrl = Arrays.asList(
                "https://graph.microsoft.com/v1.0/teams/fbe2bf47-16c8-47cf-b4a5-4b9b187c508b/channels/19:4a95f7d8db4c4e7fae857bcebe0623e6@thread.tacv2/messages/delta?$skiptoken=-FG3FPHv7HuyuazNLuy3eXlzQGbEjYLUsW9-pYkmXgn5KGsaOwrCoor2W23dGNNM1KtAX4AyvpFQNVsBgsEwUOX9lw8x9zDumgJy-C-UbjZLlZDQACyC9FyrVelZus9n.--rshdLwy_WBFJd8anPXJPbSUtUD7r3V4neB5tcrG58"
        );

        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @Test
    @Description("Test endpoint: " + PrebuiltSanitizerRules.MS_TEAMS_PATH_TEMPLATES_CHATS_MESSAGES)
    public void chats_messages() {
        String chatId = "p~12345";
        String endpoint = "https://graph.microsoft.com/" + "v1.0" + "/chats/" + chatId + "/messages";
        String jsonResponse = asJson("Chats_messages_" + "v1.0" + ".json");

        String sanitized = sanitize(endpoint, jsonResponse);

        assertPseudonymized(sanitized, "8ea0e38b-efb3-4757-924a-5f94061cf8c2", "1fb8890f-423e-4154-8fbf-db6809bc8756");
        assertRedacted(sanitized,
                "@odata.context", "https://graph.microsoft.com/v1.0/$metadata#chats('19%3A2da4c29f6d7041eca70b638b43d45437%40thread.v2')/messages",
                "@odata.count"
        );

        assertReversibleUrlTokenized(sanitized, Collections.singletonList("19:2da4c29f6d7041eca70b638b43d45437@thread.v2"));

        Collection<String> oDataUrl = Arrays.asList(
                "https://graph.microsoft.com/v1.0/chats/19:2da4c29f6d7041eca70b638b43d45437@thread.v2/messages?$top=2&$skiptoken=M2UyZDAwMDAwMDMxMzkzYTMyNjQ2MTM0NjMzMjM5NjYzNjY0MzczMDM0MzE2NTYzNjEzNzMwNjIzNjMzMzg2MjM0MzM2NDM0MzUzNDMzMzc0MDc0Njg3MjY1NjE2NDJlNzYzMjAxZThmYjY4M2Y3ODAxMDAwMDg4NjA5ODdhNzgwMTAwMDB8MTYxNjk2NDUwOTgzMg%3d%3d"
        );

        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @Test
    @Description("Test endpoint: " + PrebuiltSanitizerRules.MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALLS)
    public void communications_calls() {
        String callId = "2f1a1100-b174-40a0-aba7-0b405e01ed92";
        String endpoint = "https://graph.microsoft.com/" + "v1.0" + "/communications/calls/" + callId;
        String jsonResponse = asJson("Communications_calls_" + "v1.0" + ".json");

        String sanitized = sanitize(endpoint, jsonResponse);
        assertPseudonymized(sanitized, "112f7296-5fa4-42ca-bae8-6a692b15d4b8");
        assertRedacted(sanitized,
                "@odata.type", "#microsoft.graph.call",
                "#microsoft.graph.participantInfo",
                "#microsoft.graph.identitySet",
                "#microsoft.graph.identity",
                "@odata.context", "https://graph.microsoft.com/v1.0/$metadata#communications/calls/$entity"
        );
        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @Test
    @Description("Test endpoint: " + PrebuiltSanitizerRules.MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALL_RECORDS_REGEX)
    public void communications_callRecords() {
        String endpoint = "https://graph.microsoft.com/" + "v1.0" + "/communications/callRecords";
        String jsonResponse = asJson("Communications_callRecords_" + "v1.0" + ".json");

        String sanitized = sanitize(endpoint, jsonResponse);
        assertPseudonymized(sanitized, "821809f5-0000-0000-0000-3b5136c0e777");
        assertRedacted(sanitized,
                "@odata.context", "https://graph.microsoft.com/v1.0/$metadata#communications/callRecords(sessions(segments()))/$entity",
                "@odata.type", "#microsoft.graph.callRecords.participantEndpoint",
                "#microsoft.graph.callRecords.clientUserAgent",
                "#microsoft.graph.identitySet",
                "+5564981205182",
                "#microsoft.graph.callRecords.participantEndpoint",
                "#microsoft.graph.callRecords.clientUserAgent"
        );

        Collection<String> oDataUrl = Arrays.asList(
                "https://graph.microsoft.com/v1.0/$metadata#communications/callRecords('e523d2ed-2966-4b6b-925b-754a88034cc5')/sessions?$expand=segments&$skiptoken=abc"
        );

        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @Test
    @Description("Test endpoint: " + PrebuiltSanitizerRules.MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALL_RECORDS_REGEX)
    public void communications_callRecord() {
        String callChainId = "2f1a1100-b174-40a0-aba7-0b405e01ed92";
        String endpoint = "https://graph.microsoft.com/" + "v1.0" + "/communications/callRecords/" + callChainId;
        String jsonResponse = asJson("Communications_callRecord_" + "v1.0" + ".json");

        String sanitized = sanitize(endpoint, jsonResponse);
        assertPseudonymized(sanitized, "821809f5-0000-0000-0000-3b5136c0e777", "f69e2c00-0000-0000-0000-185e5f5f5d8a");
        assertRedacted(sanitized,
                "@odata.context", "https://graph.microsoft.com/v1.0/$metadata#communications/callRecords(sessions(segments()))/$entity",
                "@odata.type", "#microsoft.graph.callRecords.participantEndpoint",
                "#microsoft.graph.callRecords.clientUserAgent",
                "#microsoft.graph.identitySet",
                "#microsoft.graph.callRecords.participantEndpoint",
                "#microsoft.graph.callRecords.clientUserAgent"
        );

        Collection<String> oDataUrl = Arrays.asList(
                "https://graph.microsoft.com/v1.0/$metadata#communications/callRecords('e523d2ed-2966-4b6b-925b-754a88034cc5')/sessions?$expand=segments&$skiptoken=abc"
        );

        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @Test
    @Description("Test endpoint: " + PrebuiltSanitizerRules.MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALL_RECORDS_GET_DIRECT_ROUTING_CALLS)
    public void communications_callRecords_getDirectRoutingCalls() {
        String fromDateTime = "2019-11-01";
        String toDateTime = "2019-12-01";
        String endpoint = "https://graph.microsoft.com/" + "v1.0" + "/communications/callRecords/getDirectRoutingCalls(fromDateTime=" + fromDateTime + ",toDateTime=" + toDateTime + ")";
        String jsonResponse = asJson("Communications_callRecords_getDirectRoutingCalls_" + "v1.0" + ".json");

        String sanitized = sanitize(endpoint, jsonResponse);
        assertPseudonymized(sanitized, "db03c14b-06eb-4189-939b-7cbf3a20ba27");
        assertRedacted(sanitized,
                "@odata.context", "https://graph.microsoft.com/v1.0/$metadata#Collection(microsoft.graph.callRecords.directRoutingLogRow)",
                "@odata.count"
        );

        Collection<String> oDataUrl = Arrays.asList(
                "https://graph.microsoft.com/v1.0/communications/callRecords/getDirectRoutingCalls(fromDateTime=2019-11-01,toDateTime=2019-12-01)?$skip=1000"
        );

        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @Test
    @Description("Test endpoint: " + PrebuiltSanitizerRules.MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALL_RECORDS_GET_PSTN_CALLS)
    public void communications_callRecords_getPstnCalls() {
        String fromDateTime = "2019-11-01";
        String toDateTime = "2019-12-01";
        String endpoint = "https://graph.microsoft.com/" + "v1.0" + "/communications/callRecords/getPstnCalls(fromDateTime=" + fromDateTime + ",toDateTime=" + toDateTime + ")";
        String jsonResponse = asJson("Communications_callRecords_getPstnCalls_" + "v1.0" + ".json");

        String sanitized = sanitize(endpoint, jsonResponse);

        assertRedacted(sanitized,
                "@odata.context", "https://graph.microsoft.com/v1.0/$metadata#Collection(microsoft.graph.callRecords.pstnCallLogRow)",
                "@odata.count"
        );

        Collection<String> oDataUrl = Arrays.asList(
                "https://graph.microsoft.com/v1.0/communications/callRecords/getPstnCalls(from=2019-11-01,to=2019-12-01)?$skip=1000"
        );

        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @Test
    @Description("Test endpoint: " + PrebuiltSanitizerRules.MS_TEAMS_PATH_TEMPLATES_USERS_ONLINE_MEETINGS)
    public void users_onlineMeetings() {
        String userId = "p~JuB1uFI_rtVS0Ygtc3m4uxhEiLI-6vn5ySKma20etlGvAJvlFOlnYuRejZSdIm5tmHzio-TdKzazWRwL50vNeFravJETR0l1WAvE219Jwug";
        String endpoint = "https://graph.microsoft.com/" + "v1.0" + "/users/" + userId + "/onlineMeetings";
        String jsonResponse = asJson("Users_onlineMeetings_" + "v1.0" + ".json");

        String sanitized = sanitize(endpoint, jsonResponse);
        assertPseudonymized(sanitized, "112f7296-5ca-bae8-6a692b15d4b8", "5810cedeb-b2c1-e9bd5d53ec96");
        assertRedacted(sanitized,
                "@odata.type",
                "#microsoft.graph.onlineMeeting",
                "#microsoft.graph.chatInfo",
                "#microsoft.graph.meetingParticipants",
                "#microsoft.graph.identitySet",
                "#microsoft.graph.identity"
        );
        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @Test
    public void users_onlineMeetings_attendanceReports() {
        String userId = "p~JuB1uFI_rtVS0Ygtc3m4uxhEiLI-6vn5ySKma20etlGvAJvlFOlnYuRejZSdIm5tmHzio-TdKzazWRwL50vNeFravJETR0l1WAvE219Jwug";
        String endpoint = "https://graph.microsoft.com/v1.0" + "/users/" + userId + "/onlineMeetings/fakeMeeting/attendanceReports";
        String jsonResponse = asJson("Users_onlineMeetings_attendanceReports_v1.0.json");

        String sanitized = sanitize(endpoint, jsonResponse);
    }

    @Test
    public void users_onlineMeetings_attendanceReport() {
        String userId = "p~JuB1uFI_rtVS0Ygtc3m4uxhEiLI-6vn5ySKma20etlGvAJvlFOlnYuRejZSdIm5tmHzio-TdKzazWRwL50vNeFravJETR0l1WAvE219Jwug";
        String endpoint = "https://graph.microsoft.com/v1.0" + "/users/" + userId + "/onlineMeetings/fakeMeeting/attendanceReports/fakeReportId";
        String jsonResponse = asJson("Users_onlineMeetings_attendanceReport_v1.0.json");
        assertNotSanitized(jsonResponse,
                "dc17674c-81d9-4adb-bfb2-8f6a442e4623"
        );

        String sanitized = sanitize(endpoint, jsonResponse);

        assertPseudonymized(sanitized, "frederick.cormier@contoso.com");
        assertRedacted(sanitized,
                "Frederick Cormier",
                "frederick.cormier@contoso.com"
        );
        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @Override
    public void yamlLength() {
    }

    @Override
    public Stream<InvocationExample> getExamples() {
        String baseEndpoint = "https://graph.microsoft.com/v1.0";

        return Stream.of(
                InvocationExample.of(baseEndpoint + "/teams", "Teams_" + "v1.0" + ".json"),
                InvocationExample.of(baseEndpoint + "/teams/893075dd-2487-4122-925f-022c42e20265/allChannels", "Teams_allChannels_" + "v1.0" + ".json"),
                InvocationExample.of(baseEndpoint + "/users/p~JuB1uFI_rtVS0Ygtc3m4uxhEiLI-6vn5ySKma20etlGvAJvlFOlnYuRejZSdIm5tmHzio-TdKzazWRwL50vNeFravJETR0l1WAvE219Jwug/chats", "Users_chats_" + "v1.0" + ".json"),
                InvocationExample.of(baseEndpoint + "/users/p~SIoJOpeSgYF7YUPQ28IWZexVuHyN9A80SJXrbfawKpDRcddGnKI4QDKyjQI9KtjJZDb8FZ27UE_toS68FyWz7Y22fnQYLP91SHJGVwQiN3E/chats?$expand=lastMessagePreview&$skiptoken=1.kscDYs0BDcYAAAEP8D2BqlByb3BlcnRpZXOCqVN5bmNTdGF0ZdnkZXlKa1pXeHBkbVZ5WldSVFpXZHRaVzUwY3lJNlczc2ljM1JoY25RaU9pSXlNREkwTFRBBADwrnhWREV3T2pBNE9qQTNMamsxTVNzd01Eb3dNQ0lzSW1WdVpDSTZJakl3TWpRdE1EZ3RNRFZVTURJNk1UVTZNVGd1TVRFck1EQTZNREFpZlYwc0lucGxjbTlNVFZOVVJHVnNhWFpsY21Wa1UyVm5iV1Z1ZEhNaU9sdGRMQ0p6YjNKMFQzSmtaWElpT2pBc0ltbHVZMngxWkdWYVpYSnZURTFUVkNJNlptRnNjMlY5rExhc3RQYWdlU2l6ZaIyMA%3d%3d", "Users_chats_" + "v1.0" + ".json"),
                InvocationExample.of(baseEndpoint + "/teams/fbe2bf47-16c8-47cf-b4a5-4b9b187c508b/channels/19:4a95f7d8db4c4e7fae857bcebe0623e6@thread.tacv2/messages", "Teams_channels_messages_" + "v1.0" + ".json"),
                InvocationExample.of(baseEndpoint + "/teams/fbe2bf47-16c8-47cf-b4a5-4b9b187c508b/channels/19:4a95f7d8db4c4e7fae857bcebe0623e6@thread.tacv2/messages/delta", "Teams_channels_messages_delta_" + "v1.0" + ".json"),
                InvocationExample.of(baseEndpoint + "/chats/p~bWr_bA0wnI4CDi2z8MuXG2vEmijbgJ9-MX1hsrnF20ik1FwGIGIQ_uMj2B_4LQV7U7F8XPs4Nx_URHgdx-pukZu2Hb6QzmB24IBvBInSdwA/messages", "Chats_messages_" + "v1.0" + ".json"),
                InvocationExample.of(baseEndpoint + "/communications/calls/2f1a1100-b174-40a0-aba7-0b405e01ed92", "Communications_calls_" + "v1.0" + ".json"),
                InvocationExample.of(baseEndpoint + "/communications/callRecords?$expand=sessions($expand=segments)", "Communications_callRecords_" + "v1.0" + ".json"),
                InvocationExample.of(baseEndpoint + "/communications/callRecords/2f1a1100-b174-40a0-aba7-0b405e01ed92?$expand=sessions($expand=segments)", "Communications_callRecord_" + "v1.0" + ".json"),
                InvocationExample.of(baseEndpoint + "/communications/callRecords/getDirectRoutingCalls(fromDateTime=2019-11-01,toDateTime=2019-12-01)", "Communications_callRecords_getDirectRoutingCalls_" + "v1.0" + ".json"),
                InvocationExample.of(baseEndpoint + "/communications/callRecords/getPstnCalls(fromDateTime=2019-11-01,toDateTime=2019-12-01)", "Communications_callRecords_getPstnCalls_" + "v1.0" + ".json"),
                InvocationExample.of(baseEndpoint + "/users/p~JuB1uFI_rtVS0Ygtc3m4uxhEiLI-6vn5ySKma20etlGvAJvlFOlnYuRejZSdIm5tmHzio-TdKzazWRwL50vNeFravJETR0l1WAvE219Jwug/onlineMeetings", "Users_onlineMeetings_" + "v1.0" + ".json"),
                InvocationExample.of(baseEndpoint + "/users/p~ZVGC5_Azw1yfpYdTixK-261KMPVFYf9JsRSHT5xviNNc9FGanxcMYemlX4cX4uIcrYVRk1e3QgKwLiukP5AzPH7EMRKicFHLercCDEMycrs/onlineMeetings?$filter=JoinWebUrl eq 'https://teams.microsoft.com/l/meetup-join/19%3ameeting_NDJiMWI0ZTUtNDI2ZC00MjRiLWI0NDEtMjVhZTU5ZDlkZGMz%40thread.v2/0?context=%7b%22Tid%22%3a%226e4c8e9f-76cf-41d1-806e-61838b880b87%22%2c%22Oid%22%3a%226257b47d-9e87-418b-9ac2-031f09397de7%22%7d'", "Users_onlineMeetings_" + "v1.0" + ".json"),
                InvocationExample.of(baseEndpoint + "/users/p~JuB1uFI_rtVS0Ygtc3m4uxhEiLI-6vn5ySKma20etlGvAJvlFOlnYuRejZSdIm5tmHzio-TdKzazWRwL50vNeFravJETR0l1WAvE219Jwug/onlineMeetings/MSpkYzE3Njc0Yy04MWQ5LTRhZGItYmZ/attendanceReports", "Users_onlineMeetings_attendanceReports_" + "v1.0" + ".json"),
                InvocationExample.of(baseEndpoint + "/users/p~JuB1uFI_rtVS0Ygtc3m4uxhEiLI-6vn5ySKma20etlGvAJvlFOlnYuRejZSdIm5tmHzio-TdKzazWRwL50vNeFravJETR0l1WAvE219Jwug/onlineMeetings/MSpkYzE3Njc0Yy04MWQ5LTRhZGItYmZ/attendanceReports/c9b6db1c-d5eb-427d-a5c0-20088d9b22d7?$expand=attendanceRecords", "Users_onlineMeetings_attendanceReport_" + "v1.0" + ".json")
        );
    }
}