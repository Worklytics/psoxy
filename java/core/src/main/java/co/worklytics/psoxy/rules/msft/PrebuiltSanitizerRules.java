package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.rules.Rules2;
import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.rules.Transform;
import co.worklytics.psoxy.rules.zoom.ZoomTransforms;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class PrebuiltSanitizerRules {

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



    static final Rules2 OUTLOOK_MAIL = DIRECTORY.withAdditionalEndpoints(
        Rules2.Endpoint.builder()
            .pathRegex("^/(v1.0|beta)/users/[^/]*/mailboxSettings")
            .build(),
        Rules2.Endpoint.builder()
            .pathRegex("^/(v1.0|beta)/users/[^/]*/messages/[^/]*")
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
            .pathRegex("^/(v1.0|beta)/users/[^/]*/mailFolders(/SentItems|\\('SentItems'\\))/messages.*")
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


    static final Rules2 OUTLOOK_CALENDAR = DIRECTORY.withAdditionalEndpoints(
        Rules2.Endpoint.builder()
            .pathRegex("^/(v1.0|beta)/users/[^/]*/mailboxSettings")
            .build(),
        EVENT_TRANSFORMS.toBuilder()
            .pathRegex("^/(v1.0|beta)/users/[^/]*/(calendars/[^/]*/)?events.*")
            .build(),
        EVENT_TRANSFORMS.toBuilder()
            .pathRegex("^/(v1.0|beta)/users/[^/]*/calendar/calendarView(?)[^/]*")
            .build()
    );



    public static final Map<String, RuleSet> MSFT_DEFAULT_RULES_MAP =
        ImmutableMap.<String, RuleSet>builder()
            .put("azure-ad", DIRECTORY)
            .put("outlook-cal", OUTLOOK_CALENDAR)
            .put("outlook-mail", OUTLOOK_MAIL)
            .build();
}
