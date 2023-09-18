package co.worklytics.psoxy.rules.dropbox;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.RuleSet;
import lombok.Getter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

public class DropboxTests extends JavaRulesTestBaseCase {

    @Getter
    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.DROPBOX_ENDPOINTS;

    @Getter
    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .sourceKind("dropbox-business")
        .defaultScopeId("dropbox")
        .build();

    @Disabled // not reliable; seems to have different value via IntelliJ/AWS deployment and my
    // laptop's maven, which doesn't make any sense, given that binary deployed to AWS was built via
    // maven on my laptop - so something about Surefire/Junit runner used by maven??
    @Test
    void sha() {
        this.assertSha("7869e465607b7a00b4bd75a832a9ed1f811ce7f2");
    }


    @Test
    void eventList() {
        String jsonString = asJson("events.json");

        String endpoint = "https://api.dropboxapi.com/2/team_log/get_events";

        Collection<String> PII = Arrays.asList(
                "john_smith@acmecorp.com",
                "John Smith"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "john_smith@acmecorp.com");
        assertPseudonymized(sanitized, "dbid:AAHgR8xsQP48a5DQUGPo-Vxsrjd0OByVmho");
        assertRedacted(sanitized,
                "John Smith",
                "reports.xls", // file name
                "/Contract Work/Draft", // relative path, namespace
                "https://..." //photo url placeholders
        );

        // team_member_id should be included
        assertNotSanitized(jsonString, "dbmid:AAFoi-tmvRuQR0jU-3fN4B-9nZo6nHcDO9Q");

        assertUrlAllowed(endpoint);
    }

    @Test
    void eventListContinue() {
        String jsonString = asJson("events_continue.json");

        String endpoint = "https://api.dropboxapi.com/2/team_log/get_events/continue";

        Collection<String> PII = Arrays.asList(
                "john_smith@acmecorp.com",
                "John Smith"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "john_smith@acmecorp.com");
        assertPseudonymized(sanitized, "dbid:AAHgR8xsQP48a5DQUGPo-Vxsrjd0OByVmho");
        assertRedacted(sanitized,
                "John Smith",
                "reports.xls", // file name
                "/Contract Work/Draft", // relative path, namespace
                "https://..." //photo url placeholders
        );

        // team_member_id should be included
        assertNotSanitized(jsonString, "dbmid:AAFoi-tmvRuQR0jU-3fN4B-9nZo6nHcDO9Q");

        assertUrlAllowed(endpoint);
    }

    @Test
    void fileList() {
        String jsonString = asJson("file_list.json");

        String endpoint = "https://api.dropboxapi.com/2/files/list_folder";

        Collection<String> PII = Arrays.asList(
                "Imaginary User"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "dbid:AAH4f99T0taONIb-OurWxbNQ6ywGRopQngc");
        assertRedacted(sanitized,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", // content hash
                "Imaginary User", // user name
                "Prime_Numbers.txt", // file name
                "/Homework/math/Prime_Numbers.txt", // display path
                "/homework/math/prime_numbers.txt" // path
        );

        assertUrlAllowed(endpoint);
    }

    @Test
    void fileListContinue() {
        String jsonString = asJson("file_list_continue.json");

        String endpoint = "https://api.dropboxapi.com/2/files/list_folder/continue";

        Collection<String> PII = Arrays.asList(
                "Imaginary User"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "dbid:AAH4f99T0taONIb-OurWxbNQ6ywGRopQngc");
        assertRedacted(sanitized,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", // content hash
                "Imaginary User", // user name
                "Prime_Numbers.txt", // file name
                "/Homework/math/Prime_Numbers.txt", // display path
                "/homework/math/prime_numbers.txt" // path
        );

        assertUrlAllowed(endpoint);
    }

    @Test
    void fileRevision() {
        String jsonString = asJson("file_list_revision.json");

        String endpoint = "https://api.dropboxapi.com/2/files/list_revisions";

        Collection<String> PII = Arrays.asList(
                "Imaginary User"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "dbid:AAH4f99T0taONIb-OurWxbNQ6ywGRopQngc");
        assertRedacted(sanitized,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", // content hash
                "Imaginary User", // user name
                "Prime_Numbers.txt", // file name
                "/Homework/math/Prime_Numbers.txt", // display path
                "/homework/math/prime_numbers.txt" // path
        );

        assertUrlAllowed(endpoint);
    }

    @Test
    void memberList() {
        String jsonString = asJson("member_list.json");

        String endpoint = "https://api.dropboxapi.com/2/team/members/list_v2";

        Collection<String> PII = Arrays.asList(
                "Franz Ferdinand (Personal)",
                "Franz",
                "FF",
                "Ferdinand",
                "tami@seagull.com",
                "grape@strawberry.com",
                "apple@orange.com"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "dbid:AAH4f99T0taONIb-OurWxbNQ6ywGRopQngc");
        assertPseudonymized(sanitized, "grape@strawberry.com");
        assertPseudonymized(sanitized, "apple@orange.com");
        assertRedacted(sanitized,
                // photo url link
                "https://dl-web.dropbox.com/account_photo/get/dbaphid%3AAAHWGmIXV3sUuOmBfTz0wPsiqHUpBWvv3ZA?vers=1556069330102&size=128x128",
                // role name
                "User management admin" // role name
        );

        assertUrlAllowed(endpoint);

        // team member should not be pseudonymized
        assertNotSanitized(sanitized, "dbmid:FDFSVF-DFSDF");
    }

    @Test
    void memberListContinue() {
        String jsonString = asJson("member_list_continue.json");

        String endpoint = "https://api.dropboxapi.com/2/team/members/list/continue_v2";

        Collection<String> PII = Arrays.asList(
                "Franz Ferdinand (Personal)",
                "Franz",
                "FF",
                "Ferdinand",
                "tami@seagull.com",
                "grape@strawberry.com",
                "apple@orange.com"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "dbid:AAH4f99T0taONIb-OurWxbNQ6ywGRopQngc");
        assertPseudonymized(sanitized, "grape@strawberry.com");
        assertPseudonymized(sanitized, "apple@orange.com");
        assertRedacted(sanitized,
                // photo url link
                "https://dl-web.dropbox.com/account_photo/get/dbaphid%3AAAHWGmIXV3sUuOmBfTz0wPsiqHUpBWvv3ZA?vers=1556069330102&size=128x128",
                // role name
                "User management admin" // role name
        );

        assertUrlAllowed(endpoint);

        // team member should not be pseudonymized
        assertNotSanitized(sanitized, "dbmid:FDFSVF-DFSDF");
    }

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
                InvocationExample.of("https://api.dropboxapi.com/2/team_log/get_events", "events.json"),
                InvocationExample.of("https://api.dropboxapi.com/2/team_log/get_events/continue", "events_continue.json"),
                InvocationExample.of("https://api.dropboxapi.com/2/files/list_folder", "file_list.json"),
                InvocationExample.of("https://api.dropboxapi.com/2/files/list_folder/continue", "file_list_continue.json"),
                InvocationExample.of("https://api.dropboxapi.com/2/files/list_revisions", "file_list_revision.json"),
                InvocationExample.of("https://api.dropboxapi.com/2/team/members/list_v2", "member_list.json"),
                InvocationExample.of("https://api.dropboxapi.com/2/team/members/list/continue_v2", "member_list_continue.json")
        );
    }
}
