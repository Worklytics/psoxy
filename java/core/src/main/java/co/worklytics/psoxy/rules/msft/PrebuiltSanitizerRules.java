package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.ConfigRulesModule;
import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class PrebuiltSanitizerRules {

    // path templates referenced by test @Description annotations
    static final String MS_TEAMS_PATH_TEMPLATES_TEAMS = "/v1.0/teams";
    static final String MS_TEAMS_PATH_TEMPLATES_TEAMS_ALL_CHANNELS = "/v1.0/teams/{teamId}/allChannels";
    static final String MS_TEAMS_PATH_TEMPLATES_USERS_CHATS = "/v1.0/users/{userId}/chats";
    static final String MS_TEAMS_PATH_TEMPLATES_TEAMS_CHANNELS_MESSAGES = "/v1.0/teams/{teamId}/channels/{channelId}/messages";
    static final String MS_TEAMS_PATH_TEMPLATES_TEAMS_CHANNELS_MESSAGES_DELTA = "/v1.0/teams/{teamId}/channels/{channelId}/messages/delta";
    static final String MS_TEAMS_PATH_TEMPLATES_CHATS_MESSAGES = "/v1.0/chats/{chatId}/messages";
    static final String MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALLS = "/v1.0/communications/calls/{callId}";
    static final String MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALL_RECORDS_REGEX = "^/v1.0/communications/callRecords/[({]?[a-fA-F0-9]{8}[-]?([a-fA-F0-9]{4}[-]?){3}[a-fA-F0-9]{12}[})]?(\\?.*)?$";
    static final String MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALL_RECORDS_LIST_REGEX = "^/v1.0/communications/callRecords(\\?.*)?$";
    static final String MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALL_RECORDS_GET_DIRECT_ROUTING_CALLS = "/v1.0/communications/callRecords/getDirectRoutingCalls(fromDateTime={startDate},toDateTime={endDate})";
    static final String MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALL_RECORDS_GET_PSTN_CALLS = "/v1.0/communications/callRecords/getPstnCalls(fromDateTime={startDate},toDateTime={endDate})";
    static final String MS_TEAMS_PATH_TEMPLATES_USERS_ONLINE_MEETINGS = "/v1.0/users/{userId}/onlineMeetings";

    static final String MS_COPILOT_INTERACTIONS_PATH = "/beta/copilot/users/{id}/interactionHistory/getAllEnterpriseInteractions";

    static final Rules2 ENTRA_ID = Rules2.load("sources/microsoft-365/entra-id/entra-id.yaml");
    static final Rules2 ENTRA_ID_NO_MSFT_IDS = Rules2.load("sources/microsoft-365/entra-id/entra-id_no-app-ids.yaml");

    static final Rules2 OUTLOOK_CALENDAR = Rules2.load("sources/microsoft-365/outlook-cal/outlook-cal.yaml");
    static final Rules2 OUTLOOK_CALENDAR_NO_APP_IDS = Rules2.load("sources/microsoft-365/outlook-cal/outlook-cal_no-app-ids.yaml");
    static final Rules2 OUTLOOK_CALENDAR_NO_APP_IDS_NO_GROUPS = Rules2.load("sources/microsoft-365/outlook-cal/outlook-cal_no-app-ids_no-groups.yaml");

    static final Rules2 OUTLOOK_MAIL = Rules2.load("sources/microsoft-365/outlook-mail/outlook-mail.yaml");
    static final Rules2 OUTLOOK_MAIL_NO_APP_IDS = Rules2.load("sources/microsoft-365/outlook-mail/outlook-mail_no-app-ids.yaml");
    static final Rules2 OUTLOOK_MAIL_NO_APP_IDS_NO_GROUPS = Rules2.load("sources/microsoft-365/outlook-mail/outlook-mail_no-app-ids_no-groups.yaml");

    static final Rules2 MS_TEAMS = Rules2.load("sources/microsoft-365/msft-teams/msft-teams.yaml");
    static final Rules2 MS_TEAMS_NO_APP_IDS = Rules2.load("sources/microsoft-365/msft-teams/msft-teams_no-app-ids.yaml");

    static final Rules2 MS_COPILOT = Rules2.load("sources/microsoft-365/msft-copilot/msft-copilot.yaml");
    static final Rules2 MS_COPILOT_NO_APP_IDS = Rules2.load("sources/microsoft-365/msft-copilot/msft-copilot_no-app-ids.yaml");

    static final RESTRules ONE_DRIVE = Rules2.load("sources/microsoft-365/msft-onedrive/msft-onedrive.yaml");
    static final RESTRules ONE_DRIVE_NO_APP_IDS = Rules2.load("sources/microsoft-365/msft-onedrive/msft-onedrive_no-app-ids.yaml");

    public static final Map<String, RESTRules> MSFT_DEFAULT_RULES_MAP =
        ImmutableMap.<String, RESTRules>builder()
            .put("azure-ad", ENTRA_ID)
            .put("azure-ad" + ConfigRulesModule.NO_APP_IDS_SUFFIX, ENTRA_ID_NO_MSFT_IDS)
            .put("outlook-cal", OUTLOOK_CALENDAR)
            .put("outlook-cal" + ConfigRulesModule.NO_APP_IDS_SUFFIX, OUTLOOK_CALENDAR_NO_APP_IDS)
            .put("outlook-cal" + ConfigRulesModule.NO_APP_IDS_SUFFIX + "-no-groups", OUTLOOK_CALENDAR_NO_APP_IDS_NO_GROUPS)
            .put("outlook-mail", OUTLOOK_MAIL)
            .put("outlook-mail" + ConfigRulesModule.NO_APP_IDS_SUFFIX, OUTLOOK_MAIL_NO_APP_IDS)
            .put("outlook-mail" + ConfigRulesModule.NO_APP_IDS_SUFFIX + "-no-groups", OUTLOOK_MAIL_NO_APP_IDS_NO_GROUPS)
            .put("msft-teams", MS_TEAMS)
            .put("msft-teams" + ConfigRulesModule.NO_APP_IDS_SUFFIX, MS_TEAMS_NO_APP_IDS)
            .put("msft-copilot", MS_COPILOT)
            .put("msft-copilot" + ConfigRulesModule.NO_APP_IDS_SUFFIX, MS_COPILOT_NO_APP_IDS)
            .put("msft-onedrive", ONE_DRIVE)
            .put("msft-onedrive" + ConfigRulesModule.NO_APP_IDS_SUFFIX, ONE_DRIVE_NO_APP_IDS)
            .build();
}
