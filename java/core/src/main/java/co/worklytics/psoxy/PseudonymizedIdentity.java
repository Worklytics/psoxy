package co.worklytics.psoxy;

import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

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

    /**
     * convert this to Pseudonym; works ONLY if built with LEGACY format
     *
     * @return
     */
    public Pseudonym fromLegacy() {
        HashUtils hashUtils = new HashUtils();

        Pseudonym.PseudonymBuilder<?, ?> builder = Pseudonym.builder()
                .hash(hashUtils.decode(hash))
                .domain(domain);

        if (reversible != null) {
            UrlSafeTokenPseudonymEncoder encoder = new UrlSafeTokenPseudonymEncoder();
            builder.reversible(encoder.decode(reversible).getReversible());
        }

        return builder.build();
    }
}
