package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.rules.Rules2;
import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.rules.Transform;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class PrebuiltSanitizerRules {

    static final Rules2 DIRECTORY = Rules2.builder()
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/(v1.0|beta)/users/?[^/]*")
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
            .pathRegex("^/(v1.0|beta)/groups/[^/]*/members.*")
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

    static final Rules2 OUTLOOK_CALENDAR = DIRECTORY.withAdditionalEndpoints(
        Rules2.Endpoint.builder()
            .pathRegex("^/(v1.0|beta)/users/[^/]*/mailboxSettings")
            .build(),
        Rules2.Endpoint.builder()
            .pathRegex("^/(v1.0|beta)/users/[^/]*/(calendars/[^/]*/)?events.*")
            .transform(Transform.Redact.builder()
                .jsonPath("$..subject")
                .jsonPath("$..body")
                .jsonPath("$..bodyPreview")
                .jsonPath("$..emailAddress.name")
                .jsonPath("$..extensions")
                .jsonPath("$..multiValueExtendedProperties")
                .jsonPath("$..singleValueExtendedProperties")
                .jsonPath("$..location.uniqueId")
                .jsonPath("$..locations[*].uniqueId")
                .jsonPath("$..onlineMeeting.joinUrl") //sometimes contain access codes
                .build())
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$..emailAddress.address")
                .build())
            .build(),
        Rules2.Endpoint.builder()
            .pathRegex("^/(v1.0|beta)/users/[^/]*/calendar/calendarView(?)[^/]*")
            .transform(Transform.Redact.builder()
                .jsonPath("$..subject")
                .jsonPath("$..body")
                .jsonPath("$..bodyPreview")
                .jsonPath("$..emailAddress.name")
                .jsonPath("$..extensions")
                .jsonPath("$..multiValueExtendedProperties")
                .jsonPath("$..singleValueExtendedProperties")
                .jsonPath("$..location.uniqueId")
                .jsonPath("$..locations[*].uniqueId")
                .jsonPath("$..onlineMeeting.joinUrl") //sometimes contain access codes
                .build())
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$..emailAddress.address")
                .build())
            .build()
    );



    public static final Map<String, RuleSet> MSFT_DEFAULT_RULES_MAP =
        ImmutableMap.<String, RuleSet>builder()
            .put("azure-ad", DIRECTORY)
            .put("outlook-cal", OUTLOOK_CALENDAR)
            .put("outlook-mail", OUTLOOK_MAIL)
            .build();
}
