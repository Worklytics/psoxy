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
    static final Transform REDACT_ODATA_CONTEXT = Transform.Redact.builder()
        .jsonPath("$.['@odata.context']")
        .build();

    static final Transform.PseudonymizeRegexMatches PSEUDONYMIZE_PROXY_ADDRESSES = Transform.PseudonymizeRegexMatches.builder()
        .jsonPath("$..proxyAddresses[*]")
        .regex("(?i)^smtp:(.*)$")
        .build();

    static final String DIRECTORY_REGEX_USERS = "^/(v1.0|beta)/users/?[^/]*";
    static final String DIRECTORY_REGEX_USERS_BY_PSEUDO = "^/(v1.0|beta)/users(/p~[a-zA-Z0-9_-]+?)?[^/]*";
    static final String DIRECTORY_REGEX_GROUP_MEMBERS = "^/(v1.0|beta)/groups/[^/]*/members.*";

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
    static final Endpoint DIRECTORY_USERS = Endpoint.builder()
        .pathRegex(DIRECTORY_REGEX_USERS)
        .allowedQueryParams(List.of("$top","$select","$skiptoken","$orderBy","$count"))
        .transforms(USER_TRANSFORMS)
        .build();

    static final Endpoint DIRECTORY_USERS_NO_APP_IDS = Endpoint.builder()
        .pathRegex(DIRECTORY_REGEX_USERS_BY_PSEUDO)
        .allowedQueryParams(List.of("$top","$select","$skiptoken","$orderBy","$count"))
        .transforms(USER_TRANSFORMS)
        .build();

    static final Endpoint DIRECTORY_GROUPS = Endpoint.builder()
        .pathRegex("^/(v1.0|beta)/groups/?[^/]*")
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

    static final Endpoint DIRECTORY_GROUP_MEMBERS = Endpoint.builder()
        .pathRegex(DIRECTORY_REGEX_GROUP_MEMBERS)
        .allowedQueryParams(List.of("$top","$select","$skiptoken","$orderBy","$count"))
        .transforms(USER_TRANSFORMS)
        .build();

    static final Rules2 DIRECTORY = Rules2.builder()
        .endpoint(DIRECTORY_USERS)
        .endpoint(DIRECTORY_GROUPS)
        .endpoint(DIRECTORY_GROUP_MEMBERS)
        .build();

    static final Rules2 DIRECTORY_NO_GROUPS = Rules2.builder()
        .endpoint(DIRECTORY_USERS_NO_APP_IDS)
        .build();

    static final Rules2 DIRECTORY_NO_MSFT_IDS = Rules2.builder()
        .endpoint(DIRECTORY_USERS_NO_APP_IDS)
        .endpoint(DIRECTORY_GROUPS)
        .endpoint(DIRECTORY_GROUP_MEMBERS)
        .build()
        .withTransformByEndpoint(DIRECTORY_REGEX_USERS_BY_PSEUDO, Transform.Pseudonymize.builder()
            .includeReversible(true)
            .encoding(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
            .jsonPath("$..id")
            .build())
        .withTransformByEndpoint(DIRECTORY_REGEX_GROUP_MEMBERS, Transform.Pseudonymize.builder()
            .jsonPath("$..id")
            .build());

    static final Rules2 DIRECTORY_NO_MSFT_IDS_NO_GROUPS = DIRECTORY_NO_GROUPS
        .withTransformByEndpoint(DIRECTORY_REGEX_USERS_BY_PSEUDO, Transform.Pseudonymize.builder()
            .includeReversible(true)
            .encoding(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
            .jsonPath("$..id")
            .build());

    static final String OUTLOOK_PATH_REGEX_MAILBOX_SETTINGS = "^/(v1.0|beta)/users/[^/]*/mailboxSettings";
    static final String OUTLOOK_MAIL_PATH_REGEX_MESSAGES = "^/(v1.0|beta)/users/[^/]*/messages/[^/]*";
    static final String OUTLOOK_MAIL_PATH_REGEX_SENT_MESSAGES = "^/(v1.0|beta)/users/[^/]*/mailFolders(/SentItems|\\('SentItems'\\))/messages.*";

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

    static final Rules2 OUTLOOK_MAIL = DIRECTORY.withAdditionalEndpoints(OUTLOOK_MAIL_ENDPOINTS);

    static final Rules2 OUTLOOK_MAIL_NO_APP_IDS = DIRECTORY_NO_MSFT_IDS
        .withAdditionalEndpoints(OUTLOOK_MAIL_ENDPOINTS)
        .withTransformByEndpoint(OUTLOOK_MAIL_PATH_REGEX_MESSAGES, TOKENIZE_ODATA_LINKS, REDACT_ODATA_CONTEXT)
        .withTransformByEndpoint(OUTLOOK_MAIL_PATH_REGEX_SENT_MESSAGES, TOKENIZE_ODATA_LINKS, REDACT_ODATA_CONTEXT)
        .withTransformByEndpoint(OUTLOOK_PATH_REGEX_MAILBOX_SETTINGS, REDACT_ODATA_CONTEXT);

    static final Rules2 OUTLOOK_MAIL_NO_APP_IDS_NO_GROUPS = DIRECTORY_NO_MSFT_IDS_NO_GROUPS
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


    static final String OUTLOOK_CALENDAR_PATH_REGEX_EVENTS = "^/(v1.0|beta)/users/[^/]*/(((calendars/[^/]*/)?events.*)|(calendar/calendarView(?)[^/]*))";

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

    static final Rules2 OUTLOOK_CALENDAR = DIRECTORY.withAdditionalEndpoints(OUTLOOK_CALENDAR_ENDPOINTS);

    static final Transform REDACT_CALENDAR_ODATA_LINKS =
        Transform.Redact.builder()
            .jsonPath("$..['calendar@odata.associationLink', 'calendar@odata.navigationLink']")
            .build();

    static final Rules2 OUTLOOK_CALENDAR_NO_APP_IDS =
        DIRECTORY_NO_MSFT_IDS
            .withAdditionalEndpoints(OUTLOOK_CALENDAR_ENDPOINTS)
            .withTransformByEndpoint(OUTLOOK_PATH_REGEX_MAILBOX_SETTINGS, REDACT_ODATA_CONTEXT)
            .withTransformByEndpoint(OUTLOOK_CALENDAR_PATH_REGEX_EVENTS, TOKENIZE_ODATA_LINKS,
                 REDACT_ODATA_CONTEXT,
                  REDACT_CALENDAR_ODATA_LINKS);

    static final Rules2 OUTLOOK_CALENDAR_NO_APP_IDS_NO_GROUPS = DIRECTORY_NO_MSFT_IDS_NO_GROUPS
        .withAdditionalEndpoints(OUTLOOK_CALENDAR_ENDPOINTS)
        .withTransformByEndpoint(OUTLOOK_PATH_REGEX_MAILBOX_SETTINGS, REDACT_ODATA_CONTEXT)
        .withTransformByEndpoint(OUTLOOK_CALENDAR_PATH_REGEX_EVENTS, TOKENIZE_ODATA_LINKS,
            REDACT_ODATA_CONTEXT,
            REDACT_CALENDAR_ODATA_LINKS);


    static final String MS_TEAMS_PATH_REGEX_TEAMS = "^/(v1.0|beta)/teams";
    static final String MS_TEAMS_PATH_REGEX_TEAMS_ALL_CHANNELS = "^/(v1.0|beta)/teams/[^/]*/allChannels";
    static final String MS_TEAMS_PATH_REGEX_USERS_CHATS = "^/(v1.0|beta)/users/[^/]*/chats";
    static final String MS_TEAMS_PATH_REGEX_TEAMS_CHANNELS_MESSAGES = "^/(v1.0|beta)/teams/[^/]*/channels/[^/]*/messages";
    static final String MS_TEAMS_PATH_REGEX_TEAMS_CHANNELS_MESSAGES_DELTA = "^/(v1.0|beta)/teams/[^/]*/channels/[^/]*/messages/delta";
    static final String MS_TEAMS_PATH_REGEX_USERS_CHATS_MESSAGES = "^/(v1.0|beta)/users/chats/[^/]*/messages";
    static final String MS_TEAMS_PATH_REGEX_COMMUNICATIONS_CALLS = "^/(v1.0|beta)/communications/calls/[^/]*";
    static final String MS_TEAMS_PATH_REGEX_COMMUNICATIONS_CALLRECORDS = "^/(v1.0|beta)/communications/callRecords/[^/]*";
    static final String MS_TEAMS_PATH_REGEX_USERS_ONLINEMEETINGS = "^/(v1.0|beta)/users/[^/]*/onlineMeetings";


    static final List<Endpoint>  MS_TEAMS_ENDPOINTS = Arrays.asList(
        Endpoint.builder()
            .pathRegex(MS_TEAMS_PATH_REGEX_TEAMS)
            .transform(Transform.Redact.builder()
                .jsonPath("$..displayName")
                .jsonPath("$..description")
                .build())
            .allowedQueryParams(List.of("$select","$top","$skipToken","$filter", "$count"))
            .build(),
        Endpoint.builder()
            .pathRegex(MS_TEAMS_PATH_REGEX_TEAMS_ALL_CHANNELS)
            .allowedQueryParams(List.of("$select","$filter"))
            .transform(Transform.Redact.builder()
                .jsonPath("$..displayName")
                .jsonPath("$..description")
                .build())
            .build(),
        Endpoint.builder()
            .pathRegex(MS_TEAMS_PATH_REGEX_USERS_CHATS)
            .allowedQueryParams(List.of("$select","$top","$skipToken", "$filter",  "$orderBy", "$expand"))
            .build(),
        Endpoint.builder()
            .pathRegex(MS_TEAMS_PATH_REGEX_TEAMS_CHANNELS_MESSAGES)
            .allowedQueryParams(List.of("$select","$top","$skipToken","$expand"))
            .transform(Transform.Redact.builder()
                .jsonPath("$..from.user.displayName")
                .jsonPath("$..body.content")
                .jsonPath("$..attachments")
                .jsonPath("$..mentions[*].mentionText")
                .jsonPath("$..mentions[*].mentioned.user.displayName")
                .jsonPath("$..eventDetail.teamDescription")
                .jsonPath("$..eventDetail.initiator.user.displayName")
                .build())
            .build(),
        Endpoint.builder()
            .pathRegex(MS_TEAMS_PATH_REGEX_TEAMS_CHANNELS_MESSAGES_DELTA)
            .allowedQueryParams(List.of("$select","$top","$skipToken","$expand", "$deltaToken"))
            .transform(Transform.Redact.builder()
                .jsonPath("$..from.user.displayName")
                .jsonPath("$..body.content")
                .jsonPath("$..attachments")
                .jsonPath("$..mentions[*].mentionText")
                .jsonPath("$..mentions[*].mentioned.user.displayName")
                .jsonPath("$..eventDetail.teamDescription")
                .jsonPath("$..eventDetail.initiator.user.displayName")
                .build())
            .build(),
        Endpoint.builder()
            .pathRegex(MS_TEAMS_PATH_REGEX_USERS_CHATS_MESSAGES)
            .allowedQueryParams(List.of("$select","$top","$skipToken","$filter", "$orderBy", "$count", "$expand", "$format", "$search", "$skip"))
            .transform(Transform.Redact.builder()
                .jsonPath("$..from.user.displayName")
                .jsonPath("$..body.content")
                .jsonPath("$..attachments")
                .jsonPath("$..mentions[*].mentionText")
                .jsonPath("$..mentions[*].mentioned.user.displayName")
                .jsonPath("$..eventDetail.teamDescription")
                .jsonPath("$..eventDetail.initiator.user.displayName")
                .build())
            .build(),
        Endpoint.builder()
            .pathRegex(MS_TEAMS_PATH_REGEX_COMMUNICATIONS_CALLS)
            .allowedQueryParams(List.of("$select","$top","$expand"))
            .transform(Transform.Redact.builder()
                .jsonPath("$..displayName")
                .build())
            .build(),
        Endpoint.builder()
            .pathRegex(MS_TEAMS_PATH_REGEX_COMMUNICATIONS_CALLRECORDS)
            .allowedQueryParams(List.of("$select","$expand"))
            .transform(Transform.Redact.builder()
                .jsonPath("$..organizer.user.displayName")
                .jsonPath("$..participants[*].user.displayName")
                .jsonPath("$..sessions[*].caller.identity.user.displayName")
                .jsonPath("$..sessions[*].callee.identity.user.displayName")
                .jsonPath("$..sessions[*].segments[*].caller.identity.user.displayName")
                .jsonPath("$..sessions[*].segments[*].callee.identity.user.displayName")
                .build())
            .build(),
        Endpoint.builder()
            .pathRegex(MS_TEAMS_PATH_REGEX_USERS_ONLINEMEETINGS)
            .allowedQueryParams(List.of("$select","$top","$skipToken","$filter", "$orderBy", "$count", "$expand", "$format", "$search", "$skip"))
            .transform(Transform.Redact.builder()
                .jsonPath("$..participants.attendees[*].identity.user.displayName")
                .jsonPath("$..participants.organizer.identity.user.displayName")
                .jsonPath("$..subject")
                .build())
            .build()
    );

    static final Rules2 MS_TEAMS = DIRECTORY.withAdditionalEndpoints(MS_TEAMS_ENDPOINTS);

    static final Rules2 MS_TEAMS_NO_APP_IDS = DIRECTORY_NO_MSFT_IDS
        .withAdditionalEndpoints(MS_TEAMS_ENDPOINTS);

    public static final Map<String, RESTRules> MSFT_DEFAULT_RULES_MAP =
        ImmutableMap.<String, RESTRules>builder()
            .put("azure-ad", DIRECTORY)
            .put("azure-ad" + ConfigRulesModule.NO_APP_IDS_SUFFIX, DIRECTORY_NO_MSFT_IDS)
            .put("outlook-cal", OUTLOOK_CALENDAR)
            .put("outlook-cal" + ConfigRulesModule.NO_APP_IDS_SUFFIX, OUTLOOK_CALENDAR_NO_APP_IDS)
            .put("outlook-cal" + ConfigRulesModule.NO_APP_IDS_SUFFIX + "-no-groups", OUTLOOK_CALENDAR_NO_APP_IDS_NO_GROUPS)
            .put("outlook-mail", OUTLOOK_MAIL)
            .put("outlook-mail" + ConfigRulesModule.NO_APP_IDS_SUFFIX, OUTLOOK_MAIL_NO_APP_IDS)
            .put("outlook-mail" + ConfigRulesModule.NO_APP_IDS_SUFFIX + "-no-groups", OUTLOOK_MAIL_NO_APP_IDS_NO_GROUPS)
            .put("ms-teams", MS_TEAMS)
            .put("ms-teams" + ConfigRulesModule.NO_APP_IDS_SUFFIX, MS_TEAMS_NO_APP_IDS)
            .build();
}
