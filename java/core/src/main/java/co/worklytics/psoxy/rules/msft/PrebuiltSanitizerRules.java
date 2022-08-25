package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.ConfigRulesModule;
import co.worklytics.psoxy.rules.Rules2;
import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.rules.Transform;
import co.worklytics.psoxy.rules.zoom.ZoomTransforms;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class PrebuiltSanitizerRules {

    static final Transform.Tokenize TOKENIZE_ODATA_LINKS = Transform.Tokenize.builder()
        .jsonPath("$.['@odata.nextLink', '@odata.prevLink']")
        .regex("^https://graph.microsoft.com/(.*)$")
        .build();
    static final Transform REDACT_ODATA_CONTEXT = Transform.Redact.builder()
        .jsonPath("$.['@odata.context']")
        .build();

    static final String DIRECTORY_REGEX_USERS = "^/(v1.0|beta)/users/?[^/]*";
    static final String DIRECTORY_REGEX_GROUP_MEMBERS = "^/(v1.0|beta)/groups/[^/]*/members.*";
    static final Rules2 DIRECTORY = Rules2.builder()
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex(DIRECTORY_REGEX_USERS)
            .transform(Transform.Redact.builder()
                .jsonPath("$..displayName")
                .jsonPath("$..employeeId")
                .jsonPath("$..aboutMe")
                .jsonPath("$..mySite")
                .jsonPath("$..preferredName")
                .jsonPath("$..givenName")
                .jsonPath("$..surname")
                .jsonPath("$..mailNickname") //get the actual mail
                .jsonPath("$..proxyAddresses")
                .jsonPath("$..faxNumber")
                .jsonPath("$..mobilePhone")
                .jsonPath("$..businessPhones[*]")
                .build())
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$..userPrincipalName")
                .jsonPath("$..imAddresses[*]")
                .jsonPath("$..mail")
                .jsonPath("$..otherMails[*]")
                .build())
            .build())
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/(v1.0|beta)/groups/?[^/]*")
            .transform(Transform.Redact.builder()
                .jsonPath("$..owners")
                .jsonPath("$..rejectedSenders")
                .jsonPath("$..acceptedSenders")
                .jsonPath("$..members")
                .jsonPath("$..membersWithLicenseErrors")
                .build())
            .build())
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex(DIRECTORY_REGEX_GROUP_MEMBERS)
            .transform(Transform.Redact.builder()
                .jsonPath("$..displayName")
                .jsonPath("$..employeeId")
                .jsonPath("$..aboutMe")
                .jsonPath("$..mySite")
                .jsonPath("$..preferredName")
                .jsonPath("$..givenName")
                .jsonPath("$..surname")
                .jsonPath("$..mailNickname") //get the actual mail
                .jsonPath("$..proxyAddresses")
                .jsonPath("$..faxNumber")
                .jsonPath("$..mobilePhone")
                .jsonPath("$..businessPhones[*]")
                .build())
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$..userPrincipalName")
                .jsonPath("$..imAddresses[*]")
                .jsonPath("$..mail")
                .jsonPath("$..otherMails[*]")
                .build())
            .build())
        .build();

    static final Rules2 DIRECTORY_NO_MSFT_IDS = DIRECTORY
        .withTransformByEndpoint(DIRECTORY_REGEX_USERS, Transform.Pseudonymize.builder()
            .includeReversible(true)
            .encoding(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
            .jsonPath("$..id")
            .build())
        .withTransformByEndpoint(DIRECTORY_REGEX_GROUP_MEMBERS, Transform.Pseudonymize.builder()
            .jsonPath("$..id")
            .build());



    static final String OUTLOOK_MAIL_PATH_REGEX_MAILBOX_SETTINGS = "^/(v1.0|beta)/users/[^/]*/mailboxSettings";
    static final String OUTLOOK_MAIL_PATH_REGEX_MESSAGES = "^/(v1.0|beta)/users/[^/]*/messages/[^/]*";
    static final String OUTLOOK_MAIL_PATH_REGEX_SENT_MESSAGES = "^/(v1.0|beta)/users/[^/]*/mailFolders(/SentItems|\\('SentItems'\\))/messages.*";
    static final Rules2 OUTLOOK_MAIL = DIRECTORY.withAdditionalEndpoints(
        Rules2.Endpoint.builder()
            .pathRegex(OUTLOOK_MAIL_PATH_REGEX_MAILBOX_SETTINGS)
            .build(),
        Rules2.Endpoint.builder()
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
        Rules2.Endpoint.builder()
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

    static final Rules2 OUTLOOK_MAIL_NO_APP_IDS = OUTLOOK_MAIL
        .withTransformByEndpoint(OUTLOOK_MAIL_PATH_REGEX_MESSAGES, TOKENIZE_ODATA_LINKS, REDACT_ODATA_CONTEXT)
        .withTransformByEndpoint(OUTLOOK_MAIL_PATH_REGEX_SENT_MESSAGES, TOKENIZE_ODATA_LINKS, REDACT_ODATA_CONTEXT)
        .withTransformByEndpoint(OUTLOOK_MAIL_PATH_REGEX_MAILBOX_SETTINGS, REDACT_ODATA_CONTEXT);

    //transforms to apply to endpoints that return Event or Event collection
    static final Rules2.Endpoint EVENT_TRANSFORMS = Rules2.Endpoint.builder()
        .transform(Transform.Redact.builder()
            .jsonPath("$..subject")
            .jsonPath("$..emailAddress.name")
            .jsonPath("$..extensions")
            .jsonPath("$..multiValueExtendedProperties")
            .jsonPath("$..singleValueExtendedProperties")
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


    static final String OUTLOOK_CALENDAR_PATH_REGEX_EVENTS = "^/(v1.0|beta)/users/[^/]*/(calendars/[^/]*/)?events.*";
    static final String OUTLOOK_CALENDAR_PATH_REGEX_CALENDAR_VIEW = "^/(v1.0|beta)/users/[^/]*/calendar/calendarView(?)[^/]*";
    static final Rules2 OUTLOOK_CALENDAR = DIRECTORY.withAdditionalEndpoints(
        Rules2.Endpoint.builder()
            .pathRegex("^/(v1.0|beta)/users/[^/]*/mailboxSettings")
            .build(),
        EVENT_TRANSFORMS.toBuilder()
            .pathRegex(OUTLOOK_CALENDAR_PATH_REGEX_EVENTS)
            .build(),
        EVENT_TRANSFORMS.toBuilder()
            .pathRegex(OUTLOOK_CALENDAR_PATH_REGEX_CALENDAR_VIEW)
            .build()
    );

    static final Transform REDACT_CALENDAR_ODATA_LINKS =
        Transform.Redact.builder()
            .jsonPath("$..['calendar@odata.associationLink', 'calendar@odata.navigationLink']")
            .build();

    static final Rules2 OUTLOOK_CALENDAR_NO_APP_IDS = OUTLOOK_CALENDAR
        .withTransformByEndpoint(OUTLOOK_MAIL_PATH_REGEX_MAILBOX_SETTINGS, REDACT_ODATA_CONTEXT)
        .withTransformByEndpoint(OUTLOOK_CALENDAR_PATH_REGEX_EVENTS, TOKENIZE_ODATA_LINKS,
            REDACT_ODATA_CONTEXT,
            REDACT_CALENDAR_ODATA_LINKS)
        .withTransformByEndpoint(OUTLOOK_CALENDAR_PATH_REGEX_CALENDAR_VIEW, TOKENIZE_ODATA_LINKS,
            REDACT_ODATA_CONTEXT,
            REDACT_CALENDAR_ODATA_LINKS);




    public static final Map<String, RuleSet> MSFT_DEFAULT_RULES_MAP =
        ImmutableMap.<String, RuleSet>builder()
            .put("azure-ad", DIRECTORY)
            .put("azure-ad" + ConfigRulesModule.NO_APP_IDS_SUFFIX, DIRECTORY_NO_MSFT_IDS)
            .put("outlook-cal", OUTLOOK_CALENDAR)
            .put("outlook-cal" + ConfigRulesModule.NO_APP_IDS_SUFFIX, OUTLOOK_CALENDAR_NO_APP_IDS)
            .put("outlook-mail", OUTLOOK_MAIL)
            .put("outlook-mail" + ConfigRulesModule.NO_APP_IDS_SUFFIX, OUTLOOK_MAIL_NO_APP_IDS)
            .build();
}
