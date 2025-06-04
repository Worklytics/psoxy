package co.worklytics.psoxy;

import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * pseudonymized form of an account identifier
 *
 */
@JsonIgnoreProperties("scope") // ensure legacy JSON-serialized pseudonyms readable with 0.5.x
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

    /**
     * future-use. 0.4 hash for pseudonym, if `hash` is NOT 0.4.
     *
     * in the future, will be filled for LEGACY (0.3) cases; but for now, will be null (and always
     * absent from JSON-serialized form)
     *
     * this will give both DEFAULT and LEGACY hashes for pseudonyms, allowing for eventual migration
     * of LEGACY customers to DEFAULT (0.4)
     *
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String h_4;

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
