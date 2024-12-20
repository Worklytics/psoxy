package co.worklytics.psoxy;

import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
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

    /**
     * base64-url-encoded-without-padding SHA-256 hash of canonical form of the identifier, without
     * domain (or scope)
     *
     * NOTE: in legacy mode, this is base64-encoded SHA-256 hash INCLUDING scope
     *
     */
    String hash;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String original;

    /**
     * a value that, if passed back to proxy in request will be decrypted to obtain the original
     * before forwarding request to source API.
     *
     * q: in practice, will be base64-url-encoded-without-padding - specify this in interface?
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String reversible;

    /**
     * convert this to Pseudonym; works ONLY if built with DEFAULT format
     *
     * @return
     */
    public Pseudonym asPseudonym() {

        //q: what to do w original, if anything?

        UrlSafeTokenPseudonymEncoder encoder = new UrlSafeTokenPseudonymEncoder();

        byte[] decodedHash, decodedReversible;
        if (hash != null) {
            decodedHash = encoder.decode(hash).getHash();
        } else {
            decodedHash = null;
        }

        if (reversible != null) {
            decodedReversible = encoder.decode(reversible).getReversible();
        } else {
            decodedReversible = null;
        }

        return Pseudonym.builder()
            .hash(decodedHash)
            .domain(domain)
            .reversible(decodedReversible)
            .build();
    }

}
