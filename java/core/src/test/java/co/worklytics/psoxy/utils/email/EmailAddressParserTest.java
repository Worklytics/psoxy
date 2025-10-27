package co.worklytics.psoxy.utils.email;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

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
        //"luke.skywalker@[192.168.1.1],luke.skywalker",
        "\"Mary Jane\" <mary_jane@worklytics.co>,mary_jane",
        "nancy+hr@company.org,nancy+hr",
        "\"Olivia Pope\" <olivia@whitehouse.gov>,olivia",
        "<peter.parker@example.com>,peter.parker",
        "\"Rachel Green\" <rachel.green@friends.example.com>,rachel.green",
        "\"\",",
        //"user@[IPv6:2001:db8::1],user"
    })
    @ParameterizedTest
    void getLocalPart(String original, String expected) {
        assertEquals(expected, emailAddressParser.parse(original).map(EmailAddress::getLocalPart).orElse(null));
    }


    @ValueSource(strings = {
        "alice@example.com",
        "bob@example.com, charlie@example.com",
        "\"David Smith\" <david.smith@example.com>, eve@example.com",
        "frank@example.com, \"Grace Hopper\" <grace.hopper@example.com>",
        "harry@example.com, <ian@example.com>, \"Jack\" <jack@example.com>",
        "mary.jane@example.com, nancy+newsletter@company.org",
        "\"Olivia Pope\" <olivia@whitehouse.gov>, peter.parker@example.com",
        "rachel.green@friends.example.com, ross.geller@example.com",
        "\"Monica Geller\" <monica@example.com>, chandler.bing@example.com",
        "phoebe.buffay@example.com, joey.tribbiani@example.com",
        ///"\"User\" <user@[IPv6:2001:db8::1]>, admin@example.com",  // thought about allowing IPs, but weird
        "test.email+alex@leetcode.com, test.email.leet+alex@code.com",
        "\"Group\" <group@example.com>, \"Another Group\" <another.group@example.com>",
        "single.address@example.com"
    })
    @ParameterizedTest
    void isValidateAddressList_valid(String original) {
        assertTrue(emailAddressParser.isValidAddressList(original));
    }

    //NOTE: commented out some copilot-suggested cases which we don't care about ... I'm OK to accept this as valid,
    // rather than fully validating domains/ips
    @ValueSource(strings = {
        "invalid-email",
        "plainaddress",
        "@missinglocalpart.com",
        "missingdomain@.com",
        "missingatsign.com",
        "user@.invalid",
        //"user@invalid-.com",
        //"user@-invalid.com",
        "user@invalid..com",
        //"user@[192.168.1.256]", // Invalid IP
        //"user@[IPv6:12345::1]", // Invalid IPv6
        "user@domain..com",
        "\"Invalid\" <invalid-email>",
        //"user@domain, another@domain", // Invalid list
        "user@domain; another@domain"  // Invalid separator
    })
    @ParameterizedTest
    void isValidateAddressList_invalid(String original) {
        assertFalse(emailAddressParser.isValidAddressList(original));
    }

    @ValueSource(strings = {
        "",
        "    ",
        "\t",
        "\n",  // are newlines legal?? doubtful, but copilot suggested and doesn't hurt to check that doesn't blow things up
        " \t \n "
    })
    @ParameterizedTest
    void parseEmailAddressesFromHeader_empty(String headerValue) {
        List<EmailAddress> emailAddresses = emailAddressParser.parseEmailAddressesFromHeader(headerValue);
        assertNotNull(emailAddresses);
        assertTrue(emailAddresses.isEmpty());
    }

    @ValueSource(strings = {
        "bob@example.com",
        "rachel.green@friends.example.com",
        "\"Monica Geller\" <monica@example.com>",
        "\"Mónica Geller\" <monica@example.com>",
        "\"José López Ñaça\"<jose@example.com>",
        "\"张伟\" <zhangwei@example.com>",
        "phoebe.buffay@example.com",
        "test.email+alex@leetcode.com,",
        "\"Group\" <group@example.com>",
    })
    @ParameterizedTest
    void parseEmailAddressesFromHeader_single(String headerValue) {
        List<EmailAddress> emailAddresses = emailAddressParser.parseEmailAddressesFromHeader(headerValue);
        assertNotNull(emailAddresses);
        assertFalse(emailAddresses.isEmpty());
        assertEquals(1, emailAddresses.size());
        assertTrue(emailAddresses.stream().allMatch(emailAddress -> emailAddress.getLocalPart() != null && emailAddress.getDomain() != null));
    }

    @ValueSource(strings = {
        "bob@example.com, charlie@example.com",
        "\"David Smith\" <david.smith@example.com>, eve@example.com",
        "frank@example.com, \"Grace Hopper\" <grace.hopper@example.com>",
        "mary.jane@example.com, nancy+newsletter@company.org",
        "\"Olivia Pope\" <olivia@whitehouse.gov>, peter.parker@example.com",
        "rachel.green@friends.example.com, ross.geller@example.com",
        "\"Monica Geller\" <monica@example.com>, chandler.bing@example.com",
        "\"Mónica Geller\" <monica@example.com>, chandler.bing@example.com",
        "phoebe.buffay@example.com, joey.tribbiani@example.com",
        //"\"User\" <user@[IPv6:2001:db8::1]>, admin@example.com",
        "test.email+alex@leetcode.com, test.email.leet+alex@code.com",
        "\"Group\" <group@example.com>, \"Another Group\" <another.group@example.com>",
    })
    @ParameterizedTest
    void parseEmailAddressesFromHeader_multiples(String headerValue) {
        List<EmailAddress> emailAddresses = emailAddressParser.parseEmailAddressesFromHeader(headerValue);
        assertNotNull(emailAddresses);
        assertFalse(emailAddresses.isEmpty());
        assertEquals(2, emailAddresses.size());
        assertTrue(emailAddresses.stream().allMatch(emailAddress -> emailAddress.getLocalPart() != null && emailAddress.getDomain() != null));
    }

}
