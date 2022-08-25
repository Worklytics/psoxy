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

    public static final String EMAIL_SCOPE = "email";


    /**
     * scope of the identity; eg, 'email', 'slack', etc.
     *
     * identity is considered unique within its scope.
     *
     * arguably, 'scope' is another tier of 'domain'
     *
     * NOTE: `null` scope means scope is implicit from identifier's context
     *
     * @deprecated stop sending this. consumers should infer scopes based on context
     */
    @Deprecated //will be removed in v0.4; infer from context
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String scope;


    /**
     * some sort of organizational domain, if and only if identifier has an immutable 1:1
     * association with the domain.
     *
     * eg, for emails, this has the usual meaning.
     * but GitHub identifiers, it would always be null because github users may belong to multiple
     * organizations; and may join/leave
     *
     * NOTE: `null` domain may simply imply that it's implied by the identifier's context, not
     * that identity doesn't belong to a domain
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String domain;

    String hash;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String original;

    /**
     * a value that, if passed back to proxy in request will be decrypted to obtain the original
     * before forwarding request to source API.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String reversible;
}
