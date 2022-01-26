package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.Rules;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class PrebuiltSanitzerRules {

    static final Rules DIRECTORY =  Rules.builder()
        //GENERAL stuff
        .allowedEndpointRegex("^/v1.0/(groups|users)[^/]*")
        .redaction(Rules.Rule.builder()
            // https://docs.microsoft.com/en-us/graph/api/user-list?view=graph-rest-1.0&tabs=http
            .relativeUrlRegex("^/v1.0/users.*")
            .jsonPath("$..displayName")
            .jsonPath("$..employeeId")
            .jsonPath("$..aboutMe")
            .jsonPath("$..mySite")
            .jsonPath("$..preferredName")
            .jsonPath("$..givenName")
            .jsonPath("$..surname")
            .jsonPath("$..mailNickname") //get the actual mail
            .jsonPath("$..proxyAddresses")
            .build())
        .pseudonymization(Rules.Rule.builder()
            // https://docs.microsoft.com/en-us/graph/api/user-list?view=graph-rest-1.0&tabs=http
            .relativeUrlRegex("^/v1.0/users.*")
            .jsonPath("$..userPrincipalName")
            .jsonPath("$..businessPhones[*]")
            .jsonPath("$..faxNumber")
            .jsonPath("$..imAddresses[*]")
            .jsonPath("$..mail")
            .jsonPath("$..mobilePhone")
            .jsonPath("$..otherMails[*]")
            .build()
        )
        .redaction(Rules.Rule.builder()
            .relativeUrlRegex("^/v1.0/groups.*")
            .jsonPath("$..owners")
            .jsonPath("$..rejectedSenders")
            .jsonPath("$..acceptedSenders")
            .jsonPath("$..members")
            .jsonPath("$..membersWithLicenseErrors")
            .build())
        .build();

    static final Rules OUTLOOK_MAIL = Rules.builder()
        .allowedEndpointRegex("^/(v1.0|beta)/users/[^/]*/mailFolders/SentItems/.*")
        .allowedEndpointRegex("^/(v1.0|beta)/users/[^/]*/mailboxSettings")
        .pseudonymization(Rules.Rule.builder()
            .relativeUrlRegex("^/(v1.0|beta)/users/[^/]*/mailFolders/SentItems/.*")
            .jsonPath("$..emailAddress.address")
            .build())
        .redaction(Rules.Rule.builder()
            .relativeUrlRegex("^/(v1.0|beta)/users/[^/]*/mailFolders/SentItems/.*")
            .jsonPath("$..subject")
            .jsonPath("$..body")
            .jsonPath("$..bodyPreview")
            .jsonPath("$..emailAddress.name")
            .jsonPath("$..extensions")
            .jsonPath("$..multiValueExtendedProperties")
            .jsonPath("$..singleValueExtendedProperties")
            .jsonPath("$..internetMessageHeaders") //values that we care about generally parsed to other fields
            .build())
        .build();

    static final Rules OUTLOOK_CALENDAR = Rules.builder()
        .allowedEndpointRegex("^/v1.0/users/[^/]*/events.*")
        .allowedEndpointRegex("^/v1.0/users/[^/]*/calendar/events.*")
        .allowedEndpointRegex("^/v1.0/users/[^/]*/mailboxSettings")
        .pseudonymization(Rules.Rule.builder()
            .relativeUrlRegex("^/v1.0/users/*/events.*")
            .jsonPath("$..emailAddress.address")
            .build())
        .redaction(Rules.Rule.builder()
            .relativeUrlRegex("^/v1.0/users/*/events.*")
            .jsonPath("$..subject")
            .jsonPath("$..body")
            .jsonPath("$..bodyPreview")
            .jsonPath("$..emailAddress.name")
            .jsonPath("$..extensions")
            .jsonPath("$..multiValueExtendedProperties")
            .jsonPath("$..singleValueExtendedProperties")
            .build())
        .build();


    public static final Map<String,? extends Rules> MSFT_PREBUILT_RULES_MAP =
        ImmutableMap.<String, Rules>builder()
            .put("azure-ad", DIRECTORY)
            .put("outlook-cal", OUTLOOK_CALENDAR)
            .put("outlook-mail", OUTLOOK_MAIL)
            .build();
}
