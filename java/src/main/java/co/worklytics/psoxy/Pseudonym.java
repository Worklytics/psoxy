package co.worklytics.psoxy;


import lombok.Builder;
import lombok.Value;

/**
 * pseudonymized form of an account identifier
 *
 */
@Builder
@Value
public class Pseudonym {

    String hash;

    //q: include hash of canonical name as well (eg, in case of human name on email??) to aid
    // later account linking?? (eg, secondary pseudonym with scope='humanName'?)

    /**
     * some sort of organizational domain. for emails, this has usual meaning; but there are more
     * cases:
     *   - Slack cross-account messages
     *   - GitHub organizations
     */
    String domain;

    //q: include scope? (eg, 'email', 'slack', etc?)
    // 'scope' is in principle another level of 'domain' that's normally implicit from context
}
