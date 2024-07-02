package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.Rules2;
import jdk.jfr.Description;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

public class TeamsTests extends JavaRulesTestBaseCase {

    @Getter
    final Rules2 rulesUnderTest = PrebuiltSanitizerRules.MS_TEAMS;

    @Getter
    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
            .sourceFamily("microsoft-365")
            .defaultScopeId("azure-ad")
            .sourceKind("msft-teams")
            .build();

    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    @Description("Test endpoint:" + PrebuiltSanitizerRules.MS_TEAMS_PATH_TEMPLATES_TEAMS)
    public void teams(String apiVersion) {
        String endpoint = "https://graph.microsoft.com/" + apiVersion + "/teams";
        String jsonResponse = asJson("Teams_" + apiVersion + ".json");
        assertNotSanitized(jsonResponse,
                "b695c5a5-c5a5-b695-a5c5-95b6a5c595b6",
                "172b0cce-e65d-7hd4-9a49-91d9f2e8493a",
                "Contoso Team"
        );

        String sanitized = sanitize(endpoint, jsonResponse);

        assertRedacted(sanitized,
                "Contoso Team",
                "This is a Contoso team, used to showcase the range of properties supported by this API",

                "Contoso General Team",
                "This is a general Contoso team",

                "Contoso API Team",
                "This is Contoso API team"
        );
        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    @Description("Test endpoint: " + PrebuiltSanitizerRules.MS_TEAMS_PATH_TEMPLATES_TEAMS_ALL_CHANNELS)
    public void teams_allChannels(String apiVersion) {
        String teamId = "172b0cce-e65d-44ce-9a49-91d9f2e8493a";
        String endpoint = "https://graph.microsoft.com/" + apiVersion + "/teams/" + teamId + "/allChannels";
        String jsonResponse = asJson("Teams_allChannels_" + apiVersion + ".json");
        assertNotSanitized(jsonResponse,
                "19:561fbdbbfca848a484f0a6f00ce9dbbd@thread.tacv2",
                "2020-05-27T19:22:25.692Z",
                "standard",
                "b3246f44-b4gb-4627-96c6-25b18fa2c910",

                "19:561fbdbbfca848a484gabdf00ce9dbbd@thread.tacv2",
                "2020-05-27T19:22:25.692Z",
                "standard",
                "b3246f44-b4gb-5678-96c6-25b18fa2c910"
        );

        String sanitized = sanitize(endpoint, jsonResponse);
        assertRedacted(sanitized,
                "General",
                "AutoTestTeam_20210311_150740.2550_fim3udfdjen9",

                "Shared channel from Contoso"
        );
        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    @Description("Test endpoint: " + PrebuiltSanitizerRules.MS_TEAMS_PATH_TEMPLATES_USERS_CHATS)
    public void users_chats(String apiVersion) {
        String userId = "8b081ef6-4792-4def-b2c9-c363a1bf41d5";
        String endpoint = "https://graph.microsoft.com/" + apiVersion + "/users/" + userId + "/chats";
        String jsonResponse = asJson("Users_chats_" + apiVersion + ".json");
        assertNotSanitized(jsonResponse,
                "19:meeting_MjdhNjM4YzUtYzExZi00OTFkLTkzZTAtNTVlNmZmMDhkNGU2@thread.v2",
                "2020-12-08T23:53:05.801Z",
                "2020-12-08T23:58:32.511Z",
                "meeting",
                "https://teams.microsoft.com/l/chat/19%3Ameeting_MjdhNjM4YzUtYzExZi00OTFkLTkzZTAtNTVlNmZmMDhkNGU2@thread.v2/0?tenantId=b33cbe9f-8ebe-4f2a-912b-7e2a427f477f",

                "19:561082c0f3f847a58069deb8eb300807@thread.v2",
                "2020-12-03T19:41:07.054Z",
                "2020-12-08T23:53:11.012Z",
                "group",
                "https://teams.microsoft.com/l/chat/19%3A561082c0f3f847a58069deb8eb300807@thread.v2/0?tenantId=b33cbe9f-8ebe-4f2a-912b-7e2a427f477f",

                "19:d74fc2ed-cb0e-4288-a219-b5c71abaf2aa_8c0a1a67-50ce-4114-bb6c-da9c5dbcf6ca@unq.gbl.spaces",
                "2020-12-04T23:10:28.51Z",
                "2020-12-04T23:10:36.925Z",
                "oneOnOne",
                "https://teams.microsoft.com/l/chat/19%3Ad74fc2ed-cb0e-4288-a219-b5c71abaf2aa_8c0a1a67-50ce-4114-bb6c-da9c5dbcf6ca@unq.gbl.spaces/0?tenantId=b33cbe9f-8ebe-4f2a-912b-7e2a427f477f"
        );

        String sanitized = sanitize(endpoint, jsonResponse);
        assertRedacted(sanitized,
                "Meeting chat sample",
                "Group chat sample",
                "topic"
        );
        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    @Description("Test endpoint: " + PrebuiltSanitizerRules.MS_TEAMS_PATH_TEMPLATES_TEAMS_CHANNELS_MESSAGES)
    public void teams_channels_messages(String apiVersion) {
        String teamId = "172b0cce-e65d-44ce-9a49-91d9f2e8493a";
        String userId = "8b081ef6-4792-4def-b2c9-c363a1bf41d5";
        String endpoint = "https://graph.microsoft.com/" + apiVersion + "/teams/" + teamId + "/channels/" + userId + "/messages";
        String jsonResponse = asJson("Teams_channels_messages_" + apiVersion + ".json");
        assertNotSanitized(jsonResponse,
                "1616965872395",
                "message",
                "2021-03-28T21:11:12.395Z",
                "normal",
                "en-us",
                "html",
                "https://teams.microsoft.com/l/message/19%3A4a95f7d8db4c4e7fae857bcebe0623e6%40thread.tacv2/1616965872395?groupId=fbe2bf47-16c8-47cf-b4a5-4b9b187c508b&tenantId=2432b57b-0abd-43db-aa7b-16eadd115d34&createdTime=1616965872395&parentMessageId=1616965872395",
                "8ea0e38b-efb3-4757-924a-5f94061cf8c2",
                "aadUser",
                "fbe2bf47-16c8-47cf-b4a5-4b9b187c508b",
                "19:4a95f7d8db4c4e7fae857bcebe0623e6@thread.tacv2",
                "ef1c916a-3135-4417-ba27-8eb7bd084193",

                "1616963377068",
                "message",
                "2021-03-28T20:29:37.068Z",
                "normal",
                "en-us",
                "html",
                "https://teams.microsoft.com/l/message/19%3A4a95f7d8db4c4e7fae857bcebe0623e6%40thread.tacv2/1616963377068?groupId=fbe2bf47-16c8-47cf-b4a5-4b9b187c508b&tenantId=2432b57b-0abd-43db-aa7b-16eadd115d34&createdTime=1616963377068&parentMessageId=1616963377068",
                "8ea0e38b-efb3-4757-924a-5f94061cf8c2",
                "aadUser",
                "fbe2bf47-16c8-47cf-b4a5-4b9b187c508b",
                "19:4a95f7d8db4c4e7fae857bcebe0623e6@thread.tacv2",

                "1616883610266",
                "unknownFutureValue",
                "2021-03-28T03:50:10.266Z",
                "normal",
                "en-us",
                "html",
                "https://teams.microsoft.com/l/message/19%3A4a95f7d8db4c4e7fae857bcebe0623e6%40thread.tacv2/1616883610266?groupId=fbe2bf47-16c8-47cf-b4a5-4b9b187c508b&tenantId=2432b57b-0abd-43db-aa7b-16eadd115d34&createdTime=1616883610266&parentMessageId=1616883610266",
                "fbe2bf47-16c8-47cf-b4a5-4b9b187c508b",
                "19:4a95f7d8db4c4e7fae857bcebe0623e6@thread.tacv2",
                "1fb8890f-423e-4154-8fbf-db6809bc8756",
                "aadUser",
                "#microsoft.graph.teamDescriptionUpdatedEventMessageDetail"
        );

        String sanitized = sanitize(endpoint, jsonResponse);
        assertRedacted(sanitized,
                "Robin Kline",
                "Hello World <at id=\\\"0\\\">Jane Smith</at>",
                "Jane Smith",

                "<div><div><div><span><img height=\\\"145\\\" src=\\\"https://graph.microsoft.com/v1.0/teams/fbe2bf47-16c8-47cf-b4a5-4b9b187c508b/channels/19:4a95f7d8db4c4e7fae857bcebe0623e6@thread.tacv2/messages/1616963377068/hostedContents/aWQ9eF8wLXd1cy1kMS02YmI3Nzk3ZGU2MmRjODdjODA4YmQ1ZmI0OWM4NjI2ZCx0eXBlPTEsdXJsPWh0dHBzOi8vdXMtYXBpLmFzbS5za3lwZS5jb20vdjEvb2JqZWN0cy8wLXd1cy1kMS02YmI3Nzk3ZGU2MmRjODdjODA4YmQ1ZmI0OWM4NjI2ZC92aWV3cy9pbWdv/$value\\\" width=\\\"131\\\" style=\\\"vertical-align:bottom; width:131px; height:145px\\\"></span><div>&nbsp;</div></div><div><div><span><img height=\\\"65\\\" src=\\\"https://graph.microsoft.com/v1.0/teams/fbe2bf47-16c8-47cf-b4a5-4b9b187c508b/channels/19:4a95f7d8db4c4e7fae857bcebe0623e6@thread.tacv2/messages/1616963377068/hostedContents/aWQ9eF8wLXd1cy1kNi0xMzY3OTE4MzVlODIxOGZlMmUwZWEwYTA1ODAxNjRiNCx0eXBlPTEsdXJsPWh0dHBzOi8vdXMtYXBpLmFzbS5za3lwZS5jb20vdjEvb2JqZWN0cy8wLXd1cy1kNi0xMzY3OTE4MzVlODIxOGZlMmUwZWEwYTA1ODAxNjRiNC92aWV3cy9pbWdv/$value\\\" width=\\\"79\\\" style=\\\"vertical-align:bottom; width:79px; height:65px\\\"></span></div></div></div></div>",

                "<systemEventMessage/>",
                "Team for Microsoft Teams members"
        );
        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    @Description("Test endpoint: " + PrebuiltSanitizerRules.MS_TEAMS_PATH_TEMPLATES_TEAMS_CHANNELS_MESSAGES_DELTA)
    public void teams_channels_messages_delta(String apiVersion) {
        String teamId = "172b0cce-e65d-44ce-9a49-91d9f2e8493a";
        String userId = "8b081ef6-4792-4def-b2c9-c363a1bf41d5";
        String endpoint = "https://graph.microsoft.com/" + apiVersion + "/teams/" + teamId + "/channels/" + userId + "/messages/delta";
        String jsonResponse = asJson("Teams_channels_messages_delta_" + apiVersion + ".json");
        assertNotSanitized(jsonResponse,
                "1606515483514",
                "message",
                "2020-11-27T22:18:03.514Z",
                "normal",
                "en-us",
                "https://teams.microsoft.com/l/message/19%3A4a95f7d8db4c4e7fae857bcebe0623e6%40thread.tacv2/1606515483514?groupId=fbe2bf47-16c8-47cf-b4a5-4b9b187c508b&tenantId=2432b57b-0abd-43db-aa7b-16eadd115d34&createdTime=1606515483514&parentMessageId=1606515483514",
                "8ea0e38b-efb3-4757-924a-5f94061cf8c2",
                "aadUser",
                "e61ef81e-8bd8-476a-92e8-4a62f8426fca",
                "text",
                "fbe2bf47-16c8-47cf-b4a5-4b9b187c508b",
                "19:4a95f7d8db4c4e7fae857bcebe0623e6@thread.tacv2",

                "1606691795113",
                "message",
                "2020-11-29T23:16:35.113Z",
                "normal",
                "en-us",
                "https://teams.microsoft.com/l/message/19%3A4a95f7d8db4c4e7fae857bcebe0623e6%40thread.tacv2/1606691795113?groupId=fbe2bf47-16c8-47cf-b4a5-4b9b187c508b&tenantId=2432b57b-0abd-43db-aa7b-16eadd115d34&createdTime=1606691795113&parentMessageId=1606691795113",
                "8ea0e38b-efb3-4757-924a-5f94061cf8c2",
                "aadUser",
                "e61ef81e-8bd8-476a-92e8-4a62f8426fca",
                "text",
                "fbe2bf47-16c8-47cf-b4a5-4b9b187c508b",
                "19:4a95f7d8db4c4e7fae857bcebe0623e6@thread.tacv2",
                "#microsoft.graph.chatMessage"
        );

        String sanitized = sanitize(endpoint, jsonResponse);
        assertRedacted(sanitized,
                "Robin Kline",
                "Test",

                "Robin Kline",
                "HelloWorld 11/29/2020 3:16:31 PM -08:00"
        );
        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    @Description("Test endpoint: " + PrebuiltSanitizerRules.MS_TEAMS_PATH_TEMPLATES_CHATS_MESSAGES)
    public void chats_messages(String apiVersion) {
        String chatId = "fbe2bf47-16c8-47cf-b4a5-4b9b187c508b";
        String endpoint = "https://graph.microsoft.com/" + apiVersion + "/chats/" + chatId + "/messages";
        String jsonResponse = asJson("Chats_messages_" + apiVersion + ".json");
        assertNotSanitized(jsonResponse,
                "1616964509832",
                "message",
                "2021-03-28T20:48:29.832Z",
                "normal",
                "en-us",
                "8ea0e38b-efb3-4757-924a-5f94061cf8c2",
                "aadUser",
                "text",
                "Graph Members",
                "1615971548136",
                "2021-03-17T08:59:08.136Z",
                "html",
                "#microsoft.graph.chatRenamedEventMessageDetail"
        );

        String sanitized = sanitize(endpoint, jsonResponse);
        assertRedacted(sanitized,
                "Robin Kline",
                "Hello world",

                "<div><div><div><span><img height=\\\"63\\\" src=\\\"https://graph.microsoft.com/v1.0/chats/19:2da4c29f6d7041eca70b638b43d45437@thread.v2/messages/1615971548136/hostedContents/aWQ9eF8wLXd1cy1kOS1lNTRmNjM1NWYxYmJkNGQ3ZTNmNGJhZmU4NTI5MTBmNix0eXBlPTEsdXJsPWh0dHBzOi8vdXMtYXBpLmFzbS5za3lwZS5jb20vdjEvb2JqZWN0cy8wLXd1cy1kOS1lNTRmNjM1NWYxYmJkNGQ3ZTNmNGJhZmU4NTI5MTBmNi92aWV3cy9pbWdv/$value\\\" width=\\\"67\\\" style=\\\"vertical-align:bottom; width:67px; height:63px\\\"></span></div></div></div>",

                "<systemEventMessage/>"
        );
        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    @Description("Test endpoint: " + PrebuiltSanitizerRules.MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALLS)
    public void communications_calls(String apiVersion) {
        String callId = "2f1a1100-b174-40a0-aba7-0b405e01ed92";
        String endpoint = "https://graph.microsoft.com/" + apiVersion + "/communications/calls/" + callId;
        String jsonResponse = asJson("Communications_calls_" + apiVersion + ".json");
        assertNotSanitized(jsonResponse,
                "established",
                "outgoing",
                "https://bot.contoso.com/callback",
                "2891555a-92ff-42e6-80fa-6e1300c6b5c6"
        );

        String sanitized = sanitize(endpoint, jsonResponse);
        assertRedacted(sanitized,
                "Calling Bot"
        );
        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    @Description("Test endpoint: " + PrebuiltSanitizerRules.MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALL_RECORDS_REGEX)
    public void communications_callRecords(String apiVersion) {
        String callChainId = "2f1a1100-b174-40a0-aba7-0b405e01ed92";
        String endpoint = "https://graph.microsoft.com/" + apiVersion + "/communications/callRecords/" + callChainId;
        String jsonResponse = asJson("Communications_callRecords_" + apiVersion + ".json");
        assertNotSanitized(jsonResponse,
                "2020-02-25T19:00:24.582757Z",
                "2020-02-25T18:52:21.2169889Z",
                "2020-02-25T18:52:46.7640013Z",
                "e523d2ed-2966-4b6b-925b-754a88034cc5",
                "821809f5-0000-0000-0000-3b5136c0e777",
                "dc368399-474c-4d40-900c-6265431fd81f",
                "821809f5-0000-0000-0000-3b5136c0e777",
                "dc368399-474c-4d40-900c-6265431fd81f",
                "f69e2c00-0000-0000-0000-185e5f5f5d8a",
                "dc368399-474c-4d40-900c-6265431fd81f"
        );

        String sanitized = sanitize(endpoint, jsonResponse);
        assertRedacted(sanitized,
                "Abbie Wilkins",
                "Owen Franklin",

                "machineName_",
                "Default input device",
                "Microphone (Microsoft Virtual Audio Device (Simple) (WDM))"
        );
        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    @Description("Test endpoint: " + PrebuiltSanitizerRules.MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALL_RECORDS_GET_DIRECT_ROUTING_CALLS)
    public void communications_callRecords_getDirectRoutingCalls(String apiVersion) {
        String fromDateTime = "2019-11-01";
        String toDateTime = "2019-12-01";
        String endpoint = "https://graph.microsoft.com/" + apiVersion + "/communications/callRecords/getDirectRoutingCalls(fromDateTime=" + fromDateTime + ",toDateTime=" + toDateTime + ")";
        String jsonResponse = asJson("Communications_callRecords_getDirectRoutingCalls_" + apiVersion + ".json");
        assertNotSanitized(jsonResponse,
                "id", "9e8bba57-dc14-533a-a7dd-f0da6575eed1",
                "correlationId", "c98e1515-a937-4b81-b8a8-3992afde64e0",
                "userId", "db03c14b-06eb-4189-939b-7cbf3a20ba27",
                "startDateTime", "2019-11-01T00:00:25.105Z",
                "inviteDateTime", "2019-11-01T00:00:21.949Z",
                "failureDateTime", "0001-01-01T00:00:00Z",
                "endDateTime", "2019-11-01T00:00:30.105Z",
                "duration",
                "callType", "ByotIn",
                "successfulCall",
                "mediaPathLocation", "USWE",
                "signalingLocation", "EUNO",
                "finalSipCode",
                "callEndSubReason",
                "finalSipCodePhrase", "BYE",
                "trunkFullyQualifiedDomainName", "tll-audiocodes01.adatum.biz",
                "mediaBypassEnabled"
        );

        String sanitized = sanitize(endpoint, jsonResponse);
        assertRedacted(sanitized,
                "userPrincipalName", "richard.malk@contoso.com",
                "userDisplayName", "Richard Malk",
                "callerNumber", "+12345678***",
                "calleeNumber", "+01234567***"
        );
        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    @Description("Test endpoint: " + PrebuiltSanitizerRules.MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALL_RECORDS_GET_PSTN_CALLS)
    public void communications_callRecords_getPstnCalls(String apiVersion) {
        String fromDateTime = "2019-11-01";
        String toDateTime = "2019-12-01";
        String endpoint = "https://graph.microsoft.com/" + apiVersion + "/communications/callRecords/getPstnCalls(fromDateTime=" + fromDateTime + ",toDateTime=" + toDateTime + ")";
        String jsonResponse = asJson("Communications_callRecords_getPstnCalls_" + apiVersion + ".json");
        assertNotSanitized(jsonResponse,
                "id", "9c4984c7-6c3c-427d-a30c-bd0b2eacee90",
                "callId", "1835317186_112562680@61.221.3.176",
                "userId", "db03c14b-06eb-4189-939b-7cbf3a20ba27",
                "startDateTime", "2019-11-01T00:00:08.2589935Z",
                "endDateTime", "2019-11-01T00:03:47.2589935Z",
                "duration",
                "charge",
                "callType", "user_in",
                "currency", "USD",
                "usageCountryCode", "US",
                "tenantCountryCode", "US",
                "connectionCharge",
                "destinationContext",
                "destinationName", "United States",
                "conferenceId",
                "licenseCapability", "MCOPSTNU",
                "inventoryType", "Subscriber",
                "operator", "Microsoft",
                "callDurationSource", "microsoft"
        );

        String sanitized = sanitize(endpoint, jsonResponse);
        assertRedacted(sanitized,
                "userPrincipalName", "richard.malk@contoso.com",
                "userDisplayName", "Richard Malk",
                "calleeNumber", "+1234567890",
                "callerNumber", "+0123456789"
        );
        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    @Description("Test endpoint: " + PrebuiltSanitizerRules.ONLINE_MEETINGS_PATH_TEMPLATES)
    public void users_onlineMeetings(String apiVersion) {
        String userId = "dc17674c-81d9-4adb-bfb2-8f6a442e4622";
        String endpoint = "https://graph.microsoft.com/" + apiVersion + "/users/" + userId + "/onlineMeetings";
        String jsonResponse = asJson("Users_onlineMeetings_" + apiVersion + ".json");
        assertNotSanitized(jsonResponse,
                "everyone",
                "5552478",
                "5550588",
                "9999999",
                "https://dialin.teams.microsoft.com/6787A136-B9B8-4D39-846C-C0F1FF937F10?id=xxxxxxx",
                "153367081",
                "2018-05-30T00:12:19.0726086Z",
                "2018-05-30T01:00:00Z",
                "112f7296-5fa4-42ca-bae8-6a692b15d4b8_19:cbee7c1c860e465f8258e3cebf7bee0d@thread.skype",
                "https://teams.microsoft.com/l/meetup-join/19%3a:meeting_NTg0NmQ3NTctZDVkZC00YzRhLThmNmEtOGQDdmZDZk@thread.v2/0?context=%7b%22Tid%22%3a%aa67bd4c-8475-432d-bd41-39f255720e0a%22%2c%22Oid%22%3a%22112f7296-5fa4-42ca-bb15d4b8%22%7d",
                "112f7296-5ca-bae8-6a692b15d4b8",
                "5810cedeb-b2c1-e9bd5d53ec96",
                "joinMeetingId", "1234567890"
        );

        String sanitized = sanitize(endpoint, jsonResponse);
        assertRedacted(sanitized,
                "Tyler Stein",
                "Jasmine Miller",
                "Test Meeting.",
                "passcode",
                "isPasscodeRequired",
                "127.0.0.1",
                "macAddress",
                "reflexiveIPAddress",
                "relayIPAddress",
                "subnet"
        );
        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @Test
    public void users_onlineMeetings_attendanceReports() {
        String userId = "dc17674c-81d9-4adb-bfb2-8f6a442e4622";
        String endpoint = "https://graph.microsoft.com/v1.0" + "/users/" + userId + "/onlineMeetings";
        String jsonResponse = asJson("Users_onlineMeetings_attendanceReports_v1.0.json");

        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @Test
    public void users_onlineMeetings_attendanceReport() {
        String userId = "dc17674c-81d9-4adb-bfb2-8f6a442e4622";
        String endpoint = "https://graph.microsoft.com/v1.0" + "/users/" + userId + "/onlineMeetings";
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
    public Stream<InvocationExample> getExamples() {
        String apiVersion = "v1.0";
        String baseEndpoint = "https://graph.microsoft.com/" + apiVersion;

        return Stream.of(
                InvocationExample.of(baseEndpoint + "/teams", "Teams_" + apiVersion + ".json"),
                InvocationExample.of(baseEndpoint + "/teams/893075dd-2487-4122-925f-022c42e20265/allChannels", "Teams_allChannels_" + apiVersion + ".json"),
                InvocationExample.of(baseEndpoint + "/users/8b081ef6-4792-4def-b2c9-c363a1bf41d5/chats", "Users_chats_" + apiVersion + ".json"),
                InvocationExample.of(baseEndpoint + "/teams/fbe2bf47-16c8-47cf-b4a5-4b9b187c508b/channels/19:4a95f7d8db4c4e7fae857bcebe0623e6@thread.tacv2/messages", "Teams_channels_messages_" + apiVersion + ".json"),
                InvocationExample.of(baseEndpoint + "/teams/fbe2bf47-16c8-47cf-b4a5-4b9b187c508b/channels/19:4a95f7d8db4c4e7fae857bcebe0623e6@thread.tacv2/messages/delta", "Teams_channels_messages_delta_" + apiVersion + ".json"),
                InvocationExample.of(baseEndpoint + "/teams/fbe2bf47-16c8-47cf-b4a5-4b9b187c508b/channels/19:4a95f7d8db4c4e7fae857bcebe0623e6@thread.tacv2/messages/delta?$deltatoken=someToken", "Teams_channels_messages_delta_" + apiVersion + ".json"),
                InvocationExample.of(baseEndpoint + "/teams/fbe2bf47-16c8-47cf-b4a5-4b9b187c508b/channels/19:4a95f7d8db4c4e7fae857bcebe0623e6@thread.tacv2/messages/delta?$skiptoken=someToken", "Teams_channels_messages_delta_" + apiVersion + ".json"),
                InvocationExample.of(baseEndpoint + "/teams/fbe2bf47-16c8-47cf-b4a5-4b9b187c508b/channels/19:3f545ef23b56445ea4ce75d4bae8a5e0@thread.tacv2/messages/delta?$filter=lastModifiedDateTime%20gt%202015-01-01T00:00:00Z&$expand=replies", "Teams_channels_messages_delta_" + apiVersion + ".json"),
                InvocationExample.of(baseEndpoint + "/chats/19:2da4c29f6d7041eca70b638b43d45437@thread.v2/messages", "Chats_messages_" + apiVersion + ".json"),
                InvocationExample.of(baseEndpoint + "/chats/19:2da4c29f6d7041eca70b638b43d45437@thread.v2/messages", "Chats_messages_" + apiVersion + ".json"),
                InvocationExample.of(baseEndpoint + "/communications/calls/2f1a1100-b174-40a0-aba7-0b405e01ed92", "Communications_calls_" + apiVersion + ".json"),
                InvocationExample.of(baseEndpoint + "/communications/callRecords/2f1a1100-b174-40a0-aba7-0b405e01ed92?$expand=sessions($expand=segments)", "Communications_callRecords_" + apiVersion + ".json"),
                InvocationExample.of(baseEndpoint + "/communications/callRecords/getDirectRoutingCalls(fromDateTime=2019-11-01,toDateTime=2019-12-01)", "Communications_callRecords_getDirectRoutingCalls_" + apiVersion + ".json"),
                InvocationExample.of(baseEndpoint + "/communications/callRecords/getPstnCalls(fromDateTime=2019-11-01,toDateTime=2019-12-01)", "Communications_callRecords_getPstnCalls_" + apiVersion + ".json"),
                InvocationExample.of(baseEndpoint + "/users", "Users_" + apiVersion + ".json"),
                InvocationExample.of(baseEndpoint + "/users/dc17674c-81d9-4adb-bfb2-8f6a442e4622/onlineMeetings", "Users_onlineMeetings_" + apiVersion + ".json"),
                InvocationExample.of(baseEndpoint + "/users/dc17674c-81d9-4adb-bfb2-8f6a442e4622/onlineMeetings/MSpkYzE3Njc0Yy04MWQ5LTRhZGItYmZ/attendanceReports", "Users_onlineMeetings_attendanceReports_" + apiVersion + ".json"),
                InvocationExample.of(baseEndpoint + "/users/dc17674c-81d9-4adb-bfb2-8f6a442e4622/onlineMeetings/MSpkYzE3Njc0Yy04MWQ5LTRhZGItYmZ/attendanceReports/c9b6db1c-d5eb-427d-a5c0-20088d9b22d7?$expand=attendanceRecords", "Users_onlineMeetings_attendanceReport_" + apiVersion + ".json")
        );
    }
}