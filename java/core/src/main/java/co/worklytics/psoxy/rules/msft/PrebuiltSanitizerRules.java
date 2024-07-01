package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.ConfigRulesModule;
import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.generics.Calendar;
import com.avaulta.gateway.rules.Endpoint;
import co.worklytics.psoxy.rules.Rules2;
import com.avaulta.gateway.rules.transforms.Transform;
import co.worklytics.psoxy.rules.zoom.ZoomTransforms;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.google.common.collect.ImmutableMap;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class PrebuiltSanitizerRules {

    static final Transform.Tokenize TOKENIZE_ODATA_LINKS = Transform.Tokenize.builder()
            .jsonPath("$.['@odata.nextLink', '@odata.prevLink']")
            .regex("^https://graph.microsoft.com/(.*)$")
            .build();

    static final Transform.Tokenize TOKENIZE_SESSIONS_ODATA_LINKS = Transform.Tokenize.builder()
            .jsonPath("$.['sessions@odata.nextLink']")
            .regex("^https://graph.microsoft.com/(.*)$")
            .build();
    static final Transform REDACT_ODATA_CONTEXT = Transform.Redact.builder()
            .jsonPath("$..['@odata.context']")
            .build();

    static final Transform REDACT_ODATA_COUNT = Transform.Redact.builder()
            .jsonPath("$..['@odata.count']")
            .build();

    static final Transform REDACT_ODATA_TYPE = Transform.Redact.builder()
            .jsonPath("$..['@odata.type']")//['@odata.type']
            .build();

    static final Transform REDACT_ODATA_ID = Transform.Redact.builder()
            .jsonPath("$..['@odata.id']")
            .build();

    static final Transform.PseudonymizeRegexMatches PSEUDONYMIZE_PROXY_ADDRESSES = Transform.PseudonymizeRegexMatches.builder()
            .jsonPath("$..proxyAddresses[*]")
            .regex("(?i)^smtp:(.*)$")
            .build();

    static final String ENTRA_ID_REGEX_USERS = "^/v1.0/users/?[^/]*";
    static final String ENTRA_ID_REGEX_USERS_BY_PSEUDO = "^/v1.0/users(/p~[a-zA-Z0-9_-]+?)?[^/]*";
    static final String ENTRA_ID_REGEX_GROUP_MEMBERS = "^/v1.0/groups/[^/]*/members.*";

    static final List<Transform> USER_TRANSFORMS = Arrays.asList(
            PSEUDONYMIZE_PROXY_ADDRESSES,
            Transform.Redact.builder()
                    .jsonPath("$..displayName")
                    .jsonPath("$..aboutMe")
                    .jsonPath("$..mySite")
                    .jsonPath("$..preferredName")
                    .jsonPath("$..givenName")
                    .jsonPath("$..surname")
                    .jsonPath("$..mailNickname") //get the actual mail
                    .jsonPath("$..responsibilities")
                    .jsonPath("$..skills")
                    .jsonPath("$..faxNumber")
                    .jsonPath("$..mobilePhone")
                    .jsonPath("$..businessPhones[*]")
                    .jsonPath("$..onPremisesExtensionAttributes")
                    .jsonPath("$..onPremisesSecurityIdentifier")
                    .jsonPath("$..securityIdentifier")
                    .build(),
            Transform.Pseudonymize.builder()
                    .jsonPath("$..employeeId")
                    .jsonPath("$..userPrincipalName")
                    .jsonPath("$..imAddresses[*]")
                    .jsonPath("$..mail")
                    .jsonPath("$..otherMails[*]")
                    .jsonPath("$..onPremisesSamAccountName")
                    .jsonPath("$..onPremisesUserPrincipalName")
                    .jsonPath("$..onPremisesDistinguishedName")
                    .jsonPath("$..onPremisesImmutableId")
                    .jsonPath("$..identities[*].issuerAssignedId")
                    .build()
    );
    static final Endpoint ENTRA_ID_USERS = Endpoint.builder()
            .pathRegex(ENTRA_ID_REGEX_USERS)
            .allowedQueryParams(List.of("$top", "$select", "$skiptoken", "$orderBy", "$count"))
            .transforms(USER_TRANSFORMS)
            .build();

    static final Endpoint ENTRA_ID_USERS_NO_APP_IDS = Endpoint.builder()
            .pathRegex(ENTRA_ID_REGEX_USERS_BY_PSEUDO)
            .allowedQueryParams(List.of("$top", "$select", "$skiptoken", "$orderBy", "$count"))
            .transforms(USER_TRANSFORMS)
            .build();

    static final Endpoint ENTRA_ID_GROUPS = Endpoint.builder()
            .pathRegex("^/v1.0/groups/?[^/]*")
            .transform(PSEUDONYMIZE_PROXY_ADDRESSES)
            .transform(Transform.Redact.builder()
                    .jsonPath("$..owners")
                    .jsonPath("$..rejectedSenders")
                    .jsonPath("$..acceptedSenders")
                    .jsonPath("$..members")
                    .jsonPath("$..membersWithLicenseErrors")
                    .jsonPath("$..mailNickname")
                    .jsonPath("$..description") // q: include for Project use case?
                    .jsonPath("$..resourceBehaviorOptions")
                    .jsonPath("$..resourceProvisioningOptions")
                    .jsonPath("$..onPremisesSamAccountName")
                    .jsonPath("$..onPremisesSecurityIdentifier")
                    .jsonPath("$..onPremisesProvisioningErrors")
                    .jsonPath("$..securityIdentifier")
                    .build())
            .transform(Transform.Pseudonymize.builder()
                    .includeOriginal(true)
                    .jsonPath("$..mail")
                    .build())
            .build();

    static final Endpoint ENTRA_ID_GROUP_MEMBERS = Endpoint.builder()
            .pathRegex(ENTRA_ID_REGEX_GROUP_MEMBERS)
            .allowedQueryParams(List.of("$top", "$select", "$skiptoken", "$orderBy", "$count"))
            .transforms(USER_TRANSFORMS)
            .build();

    static final Rules2 ENTRA_ID = Rules2.builder()
            .endpoint(ENTRA_ID_USERS)
            .endpoint(ENTRA_ID_GROUPS)
            .endpoint(ENTRA_ID_GROUP_MEMBERS)
            .build();

    static final Rules2 ENTRA_ID_NO_GROUPS = Rules2.builder()
            .endpoint(ENTRA_ID_USERS_NO_APP_IDS)
            .build();

    static final Transform ENTRA_ID_USERS_NO_APP_IDS_TRANSFORM_RULE = Transform.Pseudonymize.builder()
            .includeReversible(true)
            .encoding(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
            .jsonPath("$..id")
            .build();
    static final Rules2 ENTRA_ID_NO_MSFT_IDS = Rules2.builder()
            .endpoint(ENTRA_ID_USERS_NO_APP_IDS)
            .endpoint(ENTRA_ID_GROUPS)
            .endpoint(ENTRA_ID_GROUP_MEMBERS)
            .build()
            .withTransformByEndpoint(ENTRA_ID_REGEX_USERS_BY_PSEUDO, ENTRA_ID_USERS_NO_APP_IDS_TRANSFORM_RULE)
            .withTransformByEndpoint(ENTRA_ID_REGEX_GROUP_MEMBERS, Transform.Pseudonymize.builder()
                    .jsonPath("$..id")
                    .build());

    static final Rules2 ENTRA_ID_NO_MSFT_IDS_NO_GROUPS = ENTRA_ID_NO_GROUPS
            .withTransformByEndpoint(ENTRA_ID_REGEX_USERS_BY_PSEUDO, Transform.Pseudonymize.builder()
                    .includeReversible(true)
                    .encoding(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
                    .jsonPath("$..id")
                    .build());

    static final String OUTLOOK_PATH_REGEX_MAILBOX_SETTINGS = "^/v1.0/users/[^/]*/mailboxSettings";
    static final String OUTLOOK_MAIL_PATH_REGEX_MESSAGES = "^/v1.0/users/[^/]*/messages/[^/]*";
    static final String OUTLOOK_MAIL_PATH_REGEX_SENT_MESSAGES = "^/v1.0/users/[^/]*/mailFolders(/SentItems|\\('SentItems'\\))/messages.*";

    static final List<Endpoint> OUTLOOK_MAIL_ENDPOINTS = Arrays.asList(
            Endpoint.builder()
                    .pathRegex(OUTLOOK_PATH_REGEX_MAILBOX_SETTINGS)
                    .transform(Transform.Redact.builder()
                            .jsonPath("$..internalReplyMessage")
                            .jsonPath("$..externalReplyMessage")
                            .build())
                    .build(),
            Endpoint.builder()
                    .pathRegex(OUTLOOK_MAIL_PATH_REGEX_MESSAGES)
                    .transform(Transform.Redact.builder()
                            .jsonPath("$..subject")
                            .jsonPath("$..body")
                            .jsonPath("$..bodyPreview")
                            .jsonPath("$..emailAddress.name")
                            .jsonPath("$..extensions")
                            .jsonPath("$..multiValueExtendedProperties")
                            .jsonPath("$..singleValueExtendedProperties")
                            .jsonPath("$..internetMessageHeaders") //values that we care about generally parsed to other fields
                            .build()
                    )
                    .transform(Transform.Pseudonymize.builder()
                            .jsonPath("$..emailAddress.address")
                            .build()
                    )
                    .build(),
            Endpoint.builder()
                    .pathRegex(OUTLOOK_MAIL_PATH_REGEX_SENT_MESSAGES)
                    .transform(Transform.Redact.builder()
                            .jsonPath("$..subject")
                            .jsonPath("$..body")
                            .jsonPath("$..bodyPreview")
                            .jsonPath("$..emailAddress.name")
                            .jsonPath("$..extensions")
                            .jsonPath("$..multiValueExtendedProperties")
                            .jsonPath("$..singleValueExtendedProperties")
                            .jsonPath("$..internetMessageHeaders") //values that we care about generally parsed to other fields
                            .build())
                    .transform(Transform.Pseudonymize.builder()
                            .jsonPath("$..emailAddress.address")
                            .build())
                    .build()
    );

    static final Rules2 OUTLOOK_MAIL = ENTRA_ID.withAdditionalEndpoints(OUTLOOK_MAIL_ENDPOINTS);

    static final Rules2 OUTLOOK_MAIL_NO_APP_IDS = ENTRA_ID_NO_MSFT_IDS
            .withAdditionalEndpoints(OUTLOOK_MAIL_ENDPOINTS)
            .withTransformByEndpoint(OUTLOOK_MAIL_PATH_REGEX_MESSAGES, TOKENIZE_ODATA_LINKS, REDACT_ODATA_CONTEXT)
            .withTransformByEndpoint(OUTLOOK_MAIL_PATH_REGEX_SENT_MESSAGES, TOKENIZE_ODATA_LINKS, REDACT_ODATA_CONTEXT)
            .withTransformByEndpoint(OUTLOOK_PATH_REGEX_MAILBOX_SETTINGS, REDACT_ODATA_CONTEXT);

    static final Rules2 OUTLOOK_MAIL_NO_APP_IDS_NO_GROUPS = ENTRA_ID_NO_MSFT_IDS_NO_GROUPS
            .withAdditionalEndpoints(OUTLOOK_MAIL_ENDPOINTS)
            .withTransformByEndpoint(OUTLOOK_MAIL_PATH_REGEX_MESSAGES, TOKENIZE_ODATA_LINKS, REDACT_ODATA_CONTEXT)
            .withTransformByEndpoint(OUTLOOK_MAIL_PATH_REGEX_SENT_MESSAGES, TOKENIZE_ODATA_LINKS, REDACT_ODATA_CONTEXT)
            .withTransformByEndpoint(OUTLOOK_PATH_REGEX_MAILBOX_SETTINGS, REDACT_ODATA_CONTEXT);


    //transforms to apply to endpoints that return Event or Event collection
    static final Endpoint EVENT_TRANSFORMS = Endpoint.builder()
            .transform(Calendar.PRESERVE_CONVENTIONAL_PHRASE_SNIPPETS.toBuilder()
                    .jsonPath("$..subject")
                    .build())
            .transform(Transform.Redact.builder()
                    .jsonPath("$..emailAddress.name")
                    .jsonPath("$..extensions")
                    .jsonPath("$..multiValueExtendedProperties")
                    .jsonPath("$..singleValueExtendedProperties")
                    .jsonPath("$..location.coordinates")
                    .jsonPath("$..locations[*].coordinates")
                    .jsonPath("$..location.address")
                    .jsonPath("$..locations[*].address")
                    .build())
            .transform(Transform.Pseudonymize.builder()
                    .jsonPath("$..emailAddress.address")
                    .build())
            .transform(ZoomTransforms.SANITIZE_JOIN_URL.toBuilder()
                    .jsonPath("$..location.uniqueId")
                    .jsonPath("$..locations[*].uniqueId")
                    .jsonPath("$..location.displayName")
                    .jsonPath("$..locations[*].displayName")
                    .jsonPath("$..location.locationUri")
                    .jsonPath("$..locations[*].locationUri")
                    .jsonPath("$..onlineMeeting.joinUrl")
                    .jsonPath("$..onlineMeetingUrl")
                    .jsonPath("$..body.content") // in case we expose this in future (currently redacted)
                    .jsonPath("$..bodyPreview") // in case we expose this in future (currently redacted)
                    .build())
            .transform(ZoomTransforms.FILTER_CONTENT_EXCEPT_ZOOM_URL.toBuilder()
                    .jsonPath("$..body.content") // in case we expose this in future (currently redacted)
                    .jsonPath("$..bodyPreview") // in case we expose this in future (currently redacted)
                    .build())
            .build();


    static final String OUTLOOK_CALENDAR_PATH_REGEX_EVENTS = "^/v1.0/users/[^/]*/(((calendars/[^/]*/)?events.*)|(calendar/calendarView(?)[^/]*))";

    static final List<Endpoint> OUTLOOK_CALENDAR_ENDPOINTS = Arrays.asList(
            Endpoint.builder()
                    .pathRegex(OUTLOOK_PATH_REGEX_MAILBOX_SETTINGS)
                    .transform(Transform.Redact.builder()
                            .jsonPath("$..internalReplyMessage")
                            .jsonPath("$..externalReplyMessage")
                            .build())
                    .build(),
            EVENT_TRANSFORMS.toBuilder()
                    .pathRegex(OUTLOOK_CALENDAR_PATH_REGEX_EVENTS)
                    .build()
    );

    static final Rules2 OUTLOOK_CALENDAR = ENTRA_ID.withAdditionalEndpoints(OUTLOOK_CALENDAR_ENDPOINTS);

    static final Transform REDACT_CALENDAR_ODATA_LINKS =
            Transform.Redact.builder()
                    .jsonPath("$..['calendar@odata.associationLink', 'calendar@odata.navigationLink']")
                    .build();

    static final Rules2 OUTLOOK_CALENDAR_NO_APP_IDS =
            ENTRA_ID_NO_MSFT_IDS
                    .withAdditionalEndpoints(OUTLOOK_CALENDAR_ENDPOINTS)
                    .withTransformByEndpoint(OUTLOOK_PATH_REGEX_MAILBOX_SETTINGS, REDACT_ODATA_CONTEXT)
                    .withTransformByEndpoint(OUTLOOK_CALENDAR_PATH_REGEX_EVENTS, TOKENIZE_ODATA_LINKS,
                            REDACT_ODATA_CONTEXT,
                            REDACT_CALENDAR_ODATA_LINKS);

    static final Rules2 OUTLOOK_CALENDAR_NO_APP_IDS_NO_GROUPS = ENTRA_ID_NO_MSFT_IDS_NO_GROUPS
            .withAdditionalEndpoints(OUTLOOK_CALENDAR_ENDPOINTS)
            .withTransformByEndpoint(OUTLOOK_PATH_REGEX_MAILBOX_SETTINGS, REDACT_ODATA_CONTEXT)
            .withTransformByEndpoint(OUTLOOK_CALENDAR_PATH_REGEX_EVENTS, TOKENIZE_ODATA_LINKS,
                    REDACT_ODATA_CONTEXT,
                    REDACT_CALENDAR_ODATA_LINKS);


    static final String MS_TEAMS_PATH_TEMPLATES_TEAMS = "/{apiVersion}/teams"; // ^/v1.0
    static final String MS_TEAMS_PATH_TEMPLATES_TEAMS_ALL_CHANNELS = "/{apiVersion}/teams/{teamId}/allChannels";
    static final String MS_TEAMS_PATH_TEMPLATES_USERS_CHATS = "/{apiVersion}/users/{userId}/chats";
    static final String MS_TEAMS_PATH_TEMPLATES_TEAMS_CHANNELS_MESSAGES = "/{apiVersion}/teams/{teamId}/channels/{channelId}/messages";
    static final String MS_TEAMS_PATH_TEMPLATES_TEAMS_CHANNELS_MESSAGES_DELTA = "/{apiVersion}/teams/{teamId}/channels/{channelId}/messages/delta";
    static final String MS_TEAMS_PATH_TEMPLATES_CHATS_MESSAGES = "/{apiVersion}/chats/{chatId}/messages";
    static final String MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALLS = "/{apiVersion}/communications/calls/{callId}";
    /*
    Unfortunately, we have to use regex expression here.
    If we use pathTemplate here: /{apiVersion}/communications/callRecords/{callChainId} - internally it would convert into
        following regular expression: ^/(?<apiVersion>[^/]+)/communications/callRecords/(?<callChainId>[^/]+)$
        and due to greedy nature of regular expression all endpoints below would match to regex above:

        E.g.
        /v1.0/communications/callRecords/e523d2ed-2966-4b6b-925b-754a88034cc5
        /v1.0/communications/callRecords/e523d2ed-2966-4b6b-925b-754a88034cc5?$expand=sessions($expand=segments)
        /v1.0/communications/callRecords/getDirectRoutingCalls(fromDateTime=2019-11-01,toDateTime=2019-12-01)
        /v1.0/communications/callRecords/getPstnCalls(fromDateTime=2019-11-01,toDateTime=2019-12-01)
        /v1.0/communications/callRecords/getDirectRoutingCalls(fromDateTime=2019-11-01,toDateTime=2019-12-01)$expand=sessions($expand=segments)
        /v1.0/communications/callRecords/getPstnCalls(fromDateTime=2019-11-01,toDateTime=2019-12-01)$expand=sessions($expand=segments)
        /v1.0/communications/callRecords/getDirectRoutingCalls(fromDateTime=2019-11-01,toDateTime=2019-12-01)$skip=100
        /v1.0/communications/callRecords/getPstnCalls(fromDateTime=2019-11-01,toDateTime=2019-12-01)$skip=100

        As you can see, first two are correct, but rest are completely different endpoints and should not match.

        So, following regular expression can handle this case:
        1. Match URL:                       ^/v1.0/communications/callRecords/
        2. Match ID:                        (?<callChainId>[({]?[a-fA-F0-9]{8}[-]?([a-fA-F0-9]{4}[-]?){3}[a-fA-F0-9]{12}[})]?)
        2. Match GraphQL query parameters: (?<queryParameters>\?[a-zA-z0-9\s\$\=\(\)]*)
    */
    static final String MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALL_RECORDS_REGEX = "^/v1.0/communications/callRecords/(?<callChainId>[({]?[a-fA-F0-9]{8}[-]?([a-fA-F0-9]{4}[-]?){3}[a-fA-F0-9]{12}[})]?)(?<queryParameters>[a-zA-z0-9\\s\\$\\=\\?\\(\\)]*)";
    static final String MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALL_RECORDS_GET_DIRECT_ROUTING_CALLS = "/{apiVersion}/communications/callRecords/getDirectRoutingCalls(fromDateTime={startDate},toDateTime={endDate})";
    static final String MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALL_RECORDS_GET_PSTN_CALLS = "/{apiVersion}/communications/callRecords/getPstnCalls(fromDateTime={startDate},toDateTime={endDate})";
    static final String MS_TEAMS_PATH_TEMPLATES_USERS_ONLINE_MEETINGS = "/{apiVersion}/users/{userId}/onlineMeetings";
    static final String MS_TEAMS_PATH_TEMPLATES_USERS_ONLINE_MEETINGS_ATTENDANCE_REPORTS = "/{apiVersion}/users/{userId}/onlineMeetings/{meetingId}/attendanceReports";
    static final String MS_TEAMS_PATH_TEMPLATES_USERS_ONLINE_MEETINGS_ATTENDANCE_REPORT = "/{apiVersion}/users/{userId}/onlineMeetings/{meetingId}/attendanceReports/{reportId}";

    static final Transform.Pseudonymize PSEUDONYMIZE_USER_ID = Transform.Pseudonymize.builder()
            .jsonPath("$..user.id")
            .jsonPath("$..userId")
            .build();

    static final Transform.Pseudonymize PSEUDONYMIZE_CALL_ID = Transform.Pseudonymize.builder()
            .jsonPath("$..callId")
            .build();

    static final Transform.Redact MS_TEAMS_TEAMS_REDACT = Transform.Redact.builder()
            .jsonPath("$..displayName")
            .jsonPath("$..description")
            .build();

    static final Transform.Pseudonymize MS_TEAMS_TEAMS_DEFAULT_PSEUDONYMIZE = Transform.Pseudonymize.builder()
            .jsonPath("$..emailAddress")
            .build();

    static final Transform.Redact MS_TEAMS_USERS_CHATS_REDACT = Transform.Redact.builder()
            .jsonPath("$..topic")
            .build();
    static final Transform.Redact MS_TEAMS_TEAMS_ALL_CHANNELS_REDACT = Transform.Redact.builder()
            .jsonPath("$..displayName")
            .jsonPath("$..description")
            .build();

    static final Transform.Redact MS_TEAMS_TEAMS_CHANNELS_MESSAGES_REDACT = Transform.Redact.builder()
            .jsonPath("$..user.displayName")
            .jsonPath("$..body.content")
            .jsonPath("$..attachments")
            .jsonPath("$..mentions[*].mentionText")
            .jsonPath("$..eventDetail.teamDescription")
            .build();

    static final Transform.Redact MS_TEAMS_TEAMS_CHANNELS_MESSAGES_DELTA_REDACT = Transform.Redact.builder()
            .jsonPath("$..user.displayName")
            .jsonPath("$..value[*].body.content")
            .jsonPath("$..value[*].attachments")
            .jsonPath("$..value[*].mentions[*].mentionText")
            .jsonPath("$..value[*].eventDetail.teamDescription")
            .build();

    static final Transform.Redact MS_TEAMS_CHATS_MESSAGES_REDACT = Transform.Redact.builder()
            .jsonPath("$..user.displayName")
            .jsonPath("$..value[*].body.content")
            .jsonPath("$..value[*].attachments")
            .jsonPath("$..value[*].mentions[*].mentionText")
            .jsonPath("$..value[*].eventDetail.teamDescription")
            .jsonPath("$..value[*].eventDetail.chatDisplayName")
            .build();

    static final Transform.Redact MS_TEAMS_COMMUNICATIONS_CALL_RECORDS_REDACT = Transform.Redact.builder()
            .jsonPath("$..user.displayName")
            .jsonPath("$..reflexiveIPAddress")
            .jsonPath("$..relayIPAddress")
            .jsonPath("$..ipAddress")
            .jsonPath("$..subnet")
            .jsonPath("$..macAddress")
            .jsonPath("$..caller.name")
            .jsonPath("$..callee.name")
            .jsonPath("$..captureDeviceName")
            .jsonPath("$..renderDeviceName")
            .build();

    static final Transform.Redact MS_TEAMS_COMMUNICATIONS_CALLS_REDACT = Transform.Redact.builder()
            .jsonPath("$..displayName")
            .build();

    static final Transform.Redact MS_TEAMS_COMMUNICATIONS_CALL_RECORDS_GET_DIRECT_ROUTING_CALLS_REDACT = Transform.Redact.builder()
            .jsonPath("$..value[*].userPrincipalName")
            .jsonPath("$..value[*].userDisplayName")
            .jsonPath("$..value[*].callerNumber")
            .jsonPath("$..value[*].calleeNumber")
            .build();

    static final Transform.Redact MS_TEAMS_COMMUNICATIONS_CALL_RECORDS_GET_PSTN_CALLS_REDACT = Transform.Redact.builder()
            .jsonPath("$..value[*].userPrincipalName")
            .jsonPath("$..value[*].userDisplayName")
            .jsonPath("$..value[*].callerNumber")
            .jsonPath("$..value[*].calleeNumber")
            .build();
    static final Transform.Redact MS_TEAMS_USERS_ONLINE_MEETINGS_REDACT = Transform.Redact.builder()
            .jsonPath("$..displayName")
            .jsonPath("$..subject")
            .jsonPath("$..joinMeetingIdSettings.isPasscodeRequired")
            .jsonPath("$..joinMeetingIdSettings.passcode")
            .build();
    static final Endpoint MS_TEAMS_TEAMS = Endpoint.builder()
            .pathTemplate(MS_TEAMS_PATH_TEMPLATES_TEAMS)
            .allowedQueryParams(List.of("$select", "$top", "$skiptoken", "$filter", "$count"))
            .transform(MS_TEAMS_TEAMS_DEFAULT_PSEUDONYMIZE)
            .build();

    static final Endpoint MS_TEAMS_TEAMS_ALL_CHANNELS = Endpoint.builder()
            .pathTemplate(MS_TEAMS_PATH_TEMPLATES_TEAMS_ALL_CHANNELS)
            .allowedQueryParams(List.of("$select", "$filter"))
            .transform(MS_TEAMS_TEAMS_DEFAULT_PSEUDONYMIZE)
            .build();

    static final Endpoint MS_TEAMS_USERS_CHATS = Endpoint.builder()
            .pathTemplate(MS_TEAMS_PATH_TEMPLATES_USERS_CHATS)
            .allowedQueryParams(List.of("$select", "$top", "$skiptoken", "$filter", "$orderby", "$expand"))
            .transform(MS_TEAMS_TEAMS_DEFAULT_PSEUDONYMIZE)
            .build();

    static final Endpoint MS_TEAMS_TEAMS_CHANNELS_MESSAGES = Endpoint.builder()
            .pathTemplate(MS_TEAMS_PATH_TEMPLATES_TEAMS_CHANNELS_MESSAGES)
            .allowedQueryParams(List.of("$select", "$top", "$skiptoken", "$expand"))
            .transform(MS_TEAMS_TEAMS_DEFAULT_PSEUDONYMIZE)
            .build();

    static final Endpoint MS_TEAMS_TEAMS_CHANNELS_MESSAGES_DELTA = Endpoint.builder()
            .pathTemplate(MS_TEAMS_PATH_TEMPLATES_TEAMS_CHANNELS_MESSAGES_DELTA)
            .allowedQueryParams(List.of("$select", "$top", "$skiptoken", "$expand", "$deltatoken", "$filter"))
            .transform(MS_TEAMS_TEAMS_DEFAULT_PSEUDONYMIZE)
            .build();

    static final Endpoint MS_TEAMS_CHATS_MESSAGES = Endpoint.builder()
            .pathTemplate(MS_TEAMS_PATH_TEMPLATES_CHATS_MESSAGES)
            .allowedQueryParams(List.of("$select", "$top", "$skiptoken", "$filter", "$orderby", "$count", "$expand", "$format", "$search", "$skip"))
            .transform(MS_TEAMS_TEAMS_DEFAULT_PSEUDONYMIZE)
            .build();

    static final Endpoint MS_TEAMS_COMMUNICATIONS_CALLS = Endpoint.builder()
            .pathTemplate(MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALLS)
            .allowedQueryParams(List.of("$select", "$top", "$expand"))
            .transform(MS_TEAMS_TEAMS_DEFAULT_PSEUDONYMIZE)
            .build();

    static final Endpoint MS_TEAMS_COMMUNICATIONS_CALL_RECORDS = Endpoint.builder()
            .pathRegex(MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALL_RECORDS_REGEX)
            .allowedQueryParams(List.of("$select", "$expand"))
            .transform(MS_TEAMS_TEAMS_DEFAULT_PSEUDONYMIZE)
            .build();

    static final Endpoint MS_TEAMS_COMMUNICATIONS_CALL_RECORDS_GET_DIRECT_ROUTING_CALLS = Endpoint.builder()
            .pathTemplate(MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALL_RECORDS_GET_DIRECT_ROUTING_CALLS)
            .allowedQueryParams(List.of("$skip"))
            .transform(MS_TEAMS_TEAMS_DEFAULT_PSEUDONYMIZE)
            .build();

    static final Endpoint MS_TEAMS_COMMUNICATIONS_CALL_RECORDS_GET_PSTN_CALLS = Endpoint.builder()
            .pathTemplate(MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALL_RECORDS_GET_PSTN_CALLS)
            .allowedQueryParams(List.of("$skip"))
            .transform(MS_TEAMS_TEAMS_DEFAULT_PSEUDONYMIZE)
            .build();

    static final Endpoint MS_TEAMS_USERS_ONLINE_MEETINGS = Endpoint.builder()
            .pathTemplate(MS_TEAMS_PATH_TEMPLATES_USERS_ONLINE_MEETINGS)
            .allowedQueryParams(List.of("$select", "$top", "$skiptoken", "$filter", "$orderby", "$count", "$expand", "$format", "$search", "$skip"))
            .transform(REDACT_ODATA_CONTEXT)
            .transform(MS_TEAMS_TEAMS_DEFAULT_PSEUDONYMIZE)
            .build();

    static final Endpoint MS_TEAMS_USERS_ONLINE_MEETINGS_ATTENDANCE_REPORTS = Endpoint.builder()
            .pathTemplate(MS_TEAMS_PATH_TEMPLATES_USERS_ONLINE_MEETINGS_ATTENDANCE_REPORTS)
            .allowedQueryParams(List.of("$select", "$top", "$skiptoken", "$filter", "$orderby", "$count", "$expand", "$format", "$search", "$skip"))
            .transform(REDACT_ODATA_CONTEXT)
            .transform(MS_TEAMS_TEAMS_DEFAULT_PSEUDONYMIZE)
            .build();

    static final Endpoint MS_TEAMS_USERS_ONLINE_MEETINGS_ATTENDANCE_REPORT = Endpoint.builder()
            .pathTemplate(MS_TEAMS_PATH_TEMPLATES_USERS_ONLINE_MEETINGS_ATTENDANCE_REPORT)
            .allowedQueryParams(List.of("$select", "$top", "$skiptoken", "$filter", "$orderby", "$count", "$expand", "$format", "$search", "$skip"))
            .transform(REDACT_ODATA_CONTEXT)
            .transform(MS_TEAMS_TEAMS_DEFAULT_PSEUDONYMIZE)
            .build();

    static final Rules2 MS_TEAMS_BASE = Rules2.builder()
            .endpoint(MS_TEAMS_TEAMS)
            .endpoint(MS_TEAMS_TEAMS_ALL_CHANNELS)
            .endpoint(MS_TEAMS_USERS_CHATS)
            .endpoint(MS_TEAMS_TEAMS_CHANNELS_MESSAGES)
            .endpoint(MS_TEAMS_TEAMS_CHANNELS_MESSAGES_DELTA)
            .endpoint(MS_TEAMS_CHATS_MESSAGES)
            .endpoint(MS_TEAMS_COMMUNICATIONS_CALLS)
            .endpoint(MS_TEAMS_COMMUNICATIONS_CALL_RECORDS)
            .endpoint(MS_TEAMS_COMMUNICATIONS_CALL_RECORDS_GET_DIRECT_ROUTING_CALLS)
            .endpoint(MS_TEAMS_COMMUNICATIONS_CALL_RECORDS_GET_PSTN_CALLS)
            .endpoint(MS_TEAMS_USERS_ONLINE_MEETINGS)
            .endpoint(MS_TEAMS_USERS_ONLINE_MEETINGS_ATTENDANCE_REPORTS)
            .endpoint(MS_TEAMS_USERS_ONLINE_MEETINGS_ATTENDANCE_REPORT)
            .build();

    static final Rules2 MS_TEAMS = MS_TEAMS_BASE
            .withAdditionalEndpoints(ENTRA_ID_USERS)
            .withTransformByEndpointTemplate(MS_TEAMS_PATH_TEMPLATES_TEAMS, MS_TEAMS_TEAMS_REDACT)
            .withTransformByEndpointTemplate(MS_TEAMS_PATH_TEMPLATES_TEAMS_ALL_CHANNELS, MS_TEAMS_TEAMS_ALL_CHANNELS_REDACT)
            .withTransformByEndpointTemplate(MS_TEAMS_PATH_TEMPLATES_USERS_CHATS, MS_TEAMS_USERS_CHATS_REDACT)
            .withTransformByEndpointTemplate(MS_TEAMS_PATH_TEMPLATES_TEAMS_CHANNELS_MESSAGES, MS_TEAMS_TEAMS_CHANNELS_MESSAGES_REDACT)
            .withTransformByEndpointTemplate(MS_TEAMS_PATH_TEMPLATES_TEAMS_CHANNELS_MESSAGES_DELTA, MS_TEAMS_TEAMS_CHANNELS_MESSAGES_DELTA_REDACT)
            .withTransformByEndpointTemplate(MS_TEAMS_PATH_TEMPLATES_CHATS_MESSAGES, MS_TEAMS_CHATS_MESSAGES_REDACT)
            .withTransformByEndpointTemplate(MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALLS, MS_TEAMS_COMMUNICATIONS_CALLS_REDACT)
            .withTransformByEndpoint(MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALL_RECORDS_REGEX, MS_TEAMS_COMMUNICATIONS_CALL_RECORDS_REDACT)
            .withTransformByEndpointTemplate(MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALL_RECORDS_GET_DIRECT_ROUTING_CALLS, MS_TEAMS_COMMUNICATIONS_CALL_RECORDS_GET_DIRECT_ROUTING_CALLS_REDACT)
            .withTransformByEndpointTemplate(MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALL_RECORDS_GET_PSTN_CALLS, MS_TEAMS_COMMUNICATIONS_CALL_RECORDS_GET_PSTN_CALLS_REDACT)
            .withTransformByEndpointTemplate(MS_TEAMS_PATH_TEMPLATES_USERS_ONLINE_MEETINGS_ATTENDANCE_REPORT, MS_TEAMS_USERS_ONLINE_MEETINGS_REDACT)
            .withTransformByEndpointTemplate(MS_TEAMS_PATH_TEMPLATES_USERS_ONLINE_MEETINGS_ATTENDANCE_REPORTS, MS_TEAMS_USERS_ONLINE_MEETINGS_REDACT)
            .withTransformByEndpointTemplate(MS_TEAMS_PATH_TEMPLATES_USERS_ONLINE_MEETINGS, MS_TEAMS_USERS_ONLINE_MEETINGS_REDACT);

    static final Rules2 MS_TEAMS_NO_USER_ID = MS_TEAMS_BASE
            .withAdditionalEndpoints(ENTRA_ID_USERS_NO_APP_IDS)
            .withTransformByEndpoint(ENTRA_ID_REGEX_USERS_BY_PSEUDO, ENTRA_ID_USERS_NO_APP_IDS_TRANSFORM_RULE)
            .withTransformByEndpointTemplate(MS_TEAMS_PATH_TEMPLATES_TEAMS, PSEUDONYMIZE_USER_ID,
                    MS_TEAMS_TEAMS_REDACT
                            .toBuilder()
                            .jsonPaths(REDACT_ODATA_CONTEXT.getJsonPaths())
                            .jsonPaths(REDACT_ODATA_ID.getJsonPaths())
                            .jsonPaths(REDACT_ODATA_TYPE.getJsonPaths())
                            .jsonPaths(REDACT_ODATA_COUNT.getJsonPaths())
                            .build())
            .withTransformByEndpointTemplate(MS_TEAMS_PATH_TEMPLATES_TEAMS_ALL_CHANNELS, PSEUDONYMIZE_USER_ID,
                    MS_TEAMS_TEAMS_ALL_CHANNELS_REDACT
                            .toBuilder()
                            .jsonPaths(REDACT_ODATA_CONTEXT.getJsonPaths())
                            .jsonPaths(REDACT_ODATA_ID.getJsonPaths())
                            .jsonPaths(REDACT_ODATA_TYPE.getJsonPaths())
                            .jsonPaths(REDACT_ODATA_COUNT.getJsonPaths())
                            .build())
            .withTransformByEndpointTemplate(MS_TEAMS_PATH_TEMPLATES_USERS_CHATS, PSEUDONYMIZE_USER_ID,
                    MS_TEAMS_USERS_CHATS_REDACT
                            .toBuilder()
                            .jsonPaths(REDACT_ODATA_CONTEXT.getJsonPaths())
                            .jsonPaths(REDACT_ODATA_COUNT.getJsonPaths())
                            .build())
            .withTransformByEndpointTemplate(MS_TEAMS_PATH_TEMPLATES_TEAMS_CHANNELS_MESSAGES, PSEUDONYMIZE_USER_ID,
                    MS_TEAMS_TEAMS_CHANNELS_MESSAGES_REDACT
                            .toBuilder()
                            .jsonPaths(REDACT_ODATA_CONTEXT.getJsonPaths())
                            .jsonPaths(REDACT_ODATA_COUNT.getJsonPaths())
                            .build(),
                    TOKENIZE_ODATA_LINKS)
            .withTransformByEndpointTemplate(MS_TEAMS_PATH_TEMPLATES_TEAMS_CHANNELS_MESSAGES_DELTA, PSEUDONYMIZE_USER_ID,
                    MS_TEAMS_TEAMS_CHANNELS_MESSAGES_DELTA_REDACT
                            .toBuilder()
                            .jsonPaths(REDACT_ODATA_CONTEXT.getJsonPaths())
                            .jsonPaths(REDACT_ODATA_COUNT.getJsonPaths())
                            .build(),
                    TOKENIZE_ODATA_LINKS)
            .withTransformByEndpointTemplate(MS_TEAMS_PATH_TEMPLATES_CHATS_MESSAGES, PSEUDONYMIZE_USER_ID,
                    MS_TEAMS_CHATS_MESSAGES_REDACT
                            .toBuilder()
                            .jsonPaths(REDACT_ODATA_CONTEXT.getJsonPaths())
                            .jsonPaths(REDACT_ODATA_COUNT.getJsonPaths())
                            .build(),
                    TOKENIZE_ODATA_LINKS)
            .withTransformByEndpointTemplate(MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALLS, PSEUDONYMIZE_USER_ID,
                    MS_TEAMS_COMMUNICATIONS_CALLS_REDACT
                            .toBuilder()
                            .jsonPaths(REDACT_ODATA_CONTEXT.getJsonPaths())
                            .jsonPaths(REDACT_ODATA_COUNT.getJsonPaths())
                            .jsonPaths(REDACT_ODATA_TYPE.getJsonPaths())
                            .build())
            .withTransformByEndpoint(MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALL_RECORDS_REGEX, PSEUDONYMIZE_USER_ID,
                    MS_TEAMS_COMMUNICATIONS_CALL_RECORDS_REDACT
                            .toBuilder()
                            .jsonPaths(REDACT_ODATA_CONTEXT.getJsonPaths())
                            .jsonPaths(REDACT_ODATA_TYPE.getJsonPaths())
                            .build(),
                    TOKENIZE_ODATA_LINKS,
                    TOKENIZE_SESSIONS_ODATA_LINKS)
            .withTransformByEndpointTemplate(MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALL_RECORDS_GET_DIRECT_ROUTING_CALLS,
                    PSEUDONYMIZE_USER_ID,
                    PSEUDONYMIZE_CALL_ID,
                    MS_TEAMS_COMMUNICATIONS_CALL_RECORDS_GET_DIRECT_ROUTING_CALLS_REDACT
                            .toBuilder()
                            .jsonPaths(REDACT_ODATA_CONTEXT.getJsonPaths())
                            .jsonPaths(REDACT_ODATA_TYPE.getJsonPaths())
                            .jsonPaths(REDACT_ODATA_COUNT.getJsonPaths())
                            .build(),
                    TOKENIZE_ODATA_LINKS)
            .withTransformByEndpointTemplate(MS_TEAMS_PATH_TEMPLATES_COMMUNICATIONS_CALL_RECORDS_GET_PSTN_CALLS,
                    PSEUDONYMIZE_USER_ID,
                    PSEUDONYMIZE_CALL_ID,
                    MS_TEAMS_COMMUNICATIONS_CALL_RECORDS_GET_PSTN_CALLS_REDACT
                            .toBuilder()
                            .jsonPaths(REDACT_ODATA_CONTEXT.getJsonPaths())
                            .jsonPaths(REDACT_ODATA_TYPE.getJsonPaths())
                            .jsonPaths(REDACT_ODATA_COUNT.getJsonPaths())
                            .build(),
                    TOKENIZE_ODATA_LINKS)
            .withTransformByEndpointTemplate(MS_TEAMS_PATH_TEMPLATES_USERS_ONLINE_MEETINGS, PSEUDONYMIZE_USER_ID,
                    MS_TEAMS_USERS_ONLINE_MEETINGS_REDACT
                            .toBuilder()
                            .jsonPaths(REDACT_ODATA_CONTEXT.getJsonPaths())
                            .jsonPaths(REDACT_ODATA_TYPE.getJsonPaths())
                            .build())
            .withTransformByEndpointTemplate(MS_TEAMS_PATH_TEMPLATES_USERS_ONLINE_MEETINGS_ATTENDANCE_REPORTS, PSEUDONYMIZE_USER_ID,
                    MS_TEAMS_USERS_ONLINE_MEETINGS_REDACT
                            .toBuilder()
                            .jsonPaths(REDACT_ODATA_CONTEXT.getJsonPaths())
                            .jsonPaths(REDACT_ODATA_TYPE.getJsonPaths())
                            .build())
            .withTransformByEndpointTemplate(MS_TEAMS_PATH_TEMPLATES_USERS_ONLINE_MEETINGS_ATTENDANCE_REPORT, PSEUDONYMIZE_USER_ID,
                    MS_TEAMS_USERS_ONLINE_MEETINGS_REDACT
                            .toBuilder()
                            .jsonPaths(REDACT_ODATA_CONTEXT.getJsonPaths())
                            .jsonPaths(REDACT_ODATA_TYPE.getJsonPaths())
                            .build());

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
                    .put("msft-teams" + ConfigRulesModule.NO_APP_IDS_SUFFIX + "-no-userIds", MS_TEAMS_NO_USER_ID)
                    .build();
}