package co.worklytics.psoxy;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * pseudonymized form of an account identifier
 *
 */
@NoArgsConstructor //for jackson
@AllArgsConstructor //for builder
@Builder
@Data
public class PseudonymizedIdentity {

    /**
     * scope of the identity; eg, 'email', 'slack', etc.
     *
     * identity is considered unique within its scope.
     */
    String scope;

    String hash;

    //q: include hash of canonical name as well (eg, in case of human name on email??) to aid
    // later account linking?? (eg, secondary pseudonym with scope='humanName'?)

    //q: include 'encrypted', optionally? eg, a value that, if passed back to proxy in URL or header
    // will be decrypted

    /**
     * some sort of organizational domain. for emails, this has usual meaning; but there are more
     * cases:
     *   - Slack cross-account messages
     *   - GitHub organizations
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String domain;

    //q: include scope? (eg, 'email', 'slack', etc?)
    // 'scope' is in principle another level of 'domain' that's normally implicit from context
}
