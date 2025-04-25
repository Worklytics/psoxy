package co.worklytics.psoxy.utils.email;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class EmailAddressParserTest {

    EmailAddressParser emailAddressParser = new EmailAddressParser();

    @CsvSource({
        "alice@worklytics.co,worklytics.co",
        "\"Alice Smith\" <alice@worklytics.co>,worklytics.co",
        ",",
        "   ,",
        "bob@example.com,example.com",
        "\"Bob Brown\" <bob.brown@example.com>,example.com",
        "bob.brown+sales@example.co.uk,example.co.uk",
        "charlie@example.io,example.io",
        "\"Charlie Chaplin\" <charlie.chaplin+test@example.io>,example.io",
        "<dave@example.com>,example.com",
        "\"Eve Adams\" <eve.adams@subdomain.worklytics.co>,subdomain.worklytics.co",
        "\"Frank\" <frank@example.com>,example.com",
        "frank@example.com,example.com",
        "\"George R.R. Martin\" <george.r.r.martin@example.com>,example.com",
        "invalid-email,",
        "\"\",",
        "<>,",
        "\"Hannah Montana\" <hannah.montana+newsletter@worklytics.co>,worklytics.co",
        "ian@worklytics.co,worklytics.co",
        "jack.o'neill@example.com,example.com",
        "\"Kate\" <kate@example.com>,example.com",
        "\"Mary Jane\" <mary_jane@worklytics.co>,worklytics.co",
        "nancy+hr@company.org,company.org",
        "\"Olivia Pope\" <olivia@whitehouse.gov>,whitehouse.gov",
        "<peter.parker@example.com>,example.com",
        "\"Rachel Green\" <rachel.green@friends.example.com>,friends.example.com",
        "\"\",",
    })
    @ParameterizedTest
    void getDomain(String original, String expected) {
        assertEquals(expected, emailAddressParser.parse(original).map(EmailAddress::getDomain).orElse(null));
    }

    @CsvSource({
        "alice@worklytics.co,alice",
        "\"Alice Smith\" <alice@worklytics.co>,alice",
        ",",
        "   ,",
        "bob@example.com,bob",
        "\"Bob Brown\" <bob.brown@example.com>,bob.brown",
        "bob.brown+sales@example.co.uk,bob.brown+sales",
        "charlie@example.io,charlie",
        "\"Charlie Chaplin\" <charlie.chaplin+test@example.io>,charlie.chaplin+test",
        "<dave@example.com>,dave",
        "\"Eve Adams\" <eve.adams@subdomain.worklytics.co>,eve.adams",
        "\"Frank\" <frank@example.com>,frank",
        "frank@example.com,frank",
        "\"George R.R. Martin\" <george.r.r.martin@example.com>,george.r.r.martin",
        "\"invalid-email\",",
        "\"\",",
        "\"<>\",",
        "\"Hannah Montana\" <hannah.montana+newsletter@worklytics.co>,hannah.montana+newsletter",
        "ian@worklytics.co,ian",
        "jack.o'neill@example.com,jack.o'neill",
        "\"Kate\" <kate@example.com>,kate",
        "luke.skywalker@[192.168.1.1],luke.skywalker",
        "\"Mary Jane\" <mary_jane@worklytics.co>,mary_jane",
        "nancy+hr@company.org,nancy+hr",
        "\"Olivia Pope\" <olivia@whitehouse.gov>,olivia",
        "<peter.parker@example.com>,peter.parker",
        "\"Rachel Green\" <rachel.green@friends.example.com>,rachel.green",
        "\"\",",
        "user@[IPv6:2001:db8::1],user"
    })
    @ParameterizedTest
    void getLocalPart(String original, String expected) {
        assertEquals(expected, emailAddressParser.parse(original).map(EmailAddress::getLocalPart).orElse(null));
    }
}
