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
     *
     * arguably, 'scope' is another tier of 'domain'
     *
     * NOTE: `null` scope means scope is implicit from identifier's context
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String scope;


    /**
     * some sort of organizational domain, if and only if identifier has an immutable 1:1
     * association with the domain.
     *
     * eg, for emails, this has the usual mean.
     * but GitHub identifiers, it would alway be null because github users may belong to multiple
     * organizations; and may join/leave
     *
     * NOTE: `null` domain may simply imply that it's implied by the identifier's context, not
     * that identity doesn't belong to a domain
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String domain;

    String hash;

    //q: include 'encrypted', optionally? eg, a value that, if passed back to proxy in URL or header
    // will be decrypted

}
